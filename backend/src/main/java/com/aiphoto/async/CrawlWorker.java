package com.aiphoto.async;

import com.aiphoto.entity.CrawlAsset;
import com.aiphoto.entity.CrawlAssetSource;
import com.aiphoto.entity.CrawlJob;
import com.aiphoto.entity.CrawlPage;
import com.aiphoto.entity.CrawlRule;
import com.aiphoto.repository.CrawlAssetRepository;
import com.aiphoto.repository.CrawlAssetSourceRepository;
import com.aiphoto.repository.CrawlJobRepository;
import com.aiphoto.repository.CrawlPageRepository;
import com.aiphoto.repository.CrawlRuleRepository;
import com.aiphoto.repository.CrawlImageSkipRepository;
import com.aiphoto.service.CrawlStagingStorageService;
import com.aiphoto.service.CrawlDiscoveryHistoryService;
import com.aiphoto.service.CrawlJobQueueService;
import com.aiphoto.service.CrawlRuleMatcher;
import com.aiphoto.service.SafeHttpFetcher;
import com.aiphoto.service.PerceptualHashService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import net.coobird.thumbnailator.Thumbnails;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlWorker {

    private static final long MAX_HTML_BYTES = 5L * 1024 * 1024;
    private final CrawlJobRepository jobRepository;
    private final CrawlRuleRepository ruleRepository;
    private final CrawlPageRepository pageRepository;
    private final CrawlAssetRepository assetRepository;
    private final SafeHttpFetcher fetcher;
    private final CrawlStagingStorageService stagingStorage;
    private final ObjectMapper objectMapper;
    private final CrawlJobQueueService queueService;
    private final PerceptualHashService perceptualHashService;
    private final CrawlDiscoveryHistoryService discoveryHistoryService;
    private final CrawlImageSkipRepository imageSkipRepository;
    private final CrawlAssetSourceRepository assetSourceRepository;

    @Async
    public void discover(Long jobId) {
        String workerId = queueService.newWorkerId();
        if (!queueService.claim(jobId, workerId)) return;
        CrawlJob job = jobRepository.findById(jobId).orElseThrow();
        CrawlRule rule = ruleSnapshot(job);
        job.setStatus(CrawlJob.Status.RUNNING);
        job.setPhase(CrawlJob.Phase.DISCOVERY);
        job.setFailCount(0);
        job.setErrorMessage(null);
        jobRepository.save(job);
        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        int discoveryFailures = 0;
        queue.add(rule.getStartUrl());
        try {
            while (!queue.isEmpty() && visited.size() < rule.getMaxListPages()
                    && pageRepository.countByJobId(jobId) < rule.getMaxDetailPages()) {
                if (stopped(jobId)) return;
                if (!queueService.renew(jobId, workerId)) return;
                String listUrl = queue.removeFirst();
                String normalized = SafeHttpFetcher.normalize(listUrl);
                if (!visited.add(normalized)) continue;
                try {
                    SafeHttpFetcher.FetchedResource resource = fetcher.fetch(
                            normalized, rule.getAllowedHosts(), MAX_HTML_BYTES,
                            rule.getMinRequestIntervalMillis());
                    requireHtml(resource.contentType());
                    Document document = Jsoup.parse(
                            new String(resource.bytes(), StandardCharsets.UTF_8), resource.finalUri().toString());
                    for (Element link : document.select(rule.getDetailSelector())) {
                        String href = link.absUrl("href");
                        if (href.isBlank()) continue;
                        String detailUrl = SafeHttpFetcher.normalize(href);
                        if (!CrawlRuleMatcher.matches(
                                detailUrl, rule.getDetailUrlIncludes(), rule.getDetailUrlExcludes())) {
                            continue;
                        }
                        String detailHash = SafeHttpFetcher.urlHash(detailUrl);
                        discoveryHistoryService.addIfNew(
                                jobId, rule.getSiteId(), href, detailUrl, detailHash);
                        if (pageRepository.countByJobId(jobId) >= rule.getMaxDetailPages()) break;
                    }
                    if (rule.getNextSelector() != null && !rule.getNextSelector().isBlank()) {
                        for (Element link : document.select(rule.getNextSelector())) {
                            String next = link.absUrl("href");
                            if (!next.isBlank()) queue.addLast(next);
                        }
                    }
                } catch (Exception exception) {
                    discoveryFailures++;
                    log.warn("Crawl job {} could not process list page {}: {}",
                            jobId, normalized, shortMessage(exception));
                }
                job = jobRepository.findById(jobId).orElseThrow();
                job.setFailCount(discoveryFailures);
                if (discoveryFailures > 0) job.setErrorMessage("有 " + discoveryFailures + " 个列表页处理失败");
                jobRepository.save(job);
                jobRepository.updateDiscoveryProgress(
                        jobId, visited.size(), (int) pageRepository.countByJobId(jobId));
            }
            if (stopped(jobId)) return;
            job = jobRepository.findById(jobId).orElseThrow();
            job.setPagesFound((int) pageRepository.countByJobId(jobId));
            job.setPhase(CrawlJob.Phase.AWAITING_CONFIRMATION);
            job.setStatus(discoveryFailures == 0 ? CrawlJob.Status.COMPLETED : CrawlJob.Status.PARTIAL);
            clearLease(job);
            jobRepository.save(job);
        } catch (Exception exception) {
            failJob(job, workerId, exception);
        }
    }

    @Async
    public void download(Long jobId) {
        String workerId = queueService.newWorkerId();
        if (!queueService.claim(jobId, workerId)) return;
        CrawlJob job = jobRepository.findById(jobId).orElseThrow();
        CrawlRule rule = ruleSnapshot(job);
        job.setPhase(CrawlJob.Phase.DOWNLOAD);
        job.setStatus(CrawlJob.Status.RUNNING);
        job.setErrorMessage(null);
        jobRepository.save(job);
        recoverInterruptedItems(jobId);
        int priorFailures = job.getFailCount();
        int failures = 0;
        try {
            for (CrawlPage page : pageRepository.findByJobIdAndStatusAndIncludedTrueOrderById(
                    jobId, CrawlPage.Status.PENDING)) {
                if (stopped(jobId)) return;
                if (!queueService.renew(jobId, workerId)) return;
                if (assetRepository.countByJobIdAndStatus(jobId, CrawlAsset.Status.DOWNLOADED)
                        >= rule.getMaxImages()) break;
                page.setStatus(CrawlPage.Status.RUNNING);
                pageRepository.save(page);
                try {
                    ArrayDeque<String> detailQueue = new ArrayDeque<>();
                    Set<String> detailVisited = new HashSet<>();
                    detailQueue.add(page.getUrl());
                    while (!detailQueue.isEmpty() && detailVisited.size() < rule.getMaxPagesPerDetail()) {
                        if (stopped(jobId) || !queueService.renew(jobId, workerId)) return;
                        String detailUrl = SafeHttpFetcher.normalize(detailQueue.removeFirst());
                        if (!detailVisited.add(detailUrl)) continue;
                        SafeHttpFetcher.FetchedResource resource = fetcher.fetch(
                                detailUrl, rule.getAllowedHosts(), MAX_HTML_BYTES,
                                rule.getMinRequestIntervalMillis());
                        requireHtml(resource.contentType());
                        Document document = Jsoup.parse(
                                new String(resource.bytes(), StandardCharsets.UTF_8), resource.finalUri().toString());
                        for (Element element : document.select(rule.getImageSelector())) {
                            if (assetRepository.countByJobIdAndStatus(jobId, CrawlAsset.Status.DOWNLOADED)
                                    >= rule.getMaxImages()) break;
                            String imageUrl = CrawlRuleMatcher.extractImageUrl(
                                    element, rule.getImageAttributes(), resource.finalUri());
                            if (imageUrl == null) continue;
                            if (!CrawlRuleMatcher.matches(
                                    imageUrl, rule.getImageUrlIncludes(), rule.getImageUrlExcludes())) {
                                continue;
                            }
                            downloadAsset(jobId, job.getOwnerId(), page, rule, imageUrl);
                        }
                        if (rule.getDetailNextSelector() != null && !rule.getDetailNextSelector().isBlank()) {
                            for (Element link : document.select(rule.getDetailNextSelector())) {
                                String next = link.absUrl("href");
                                if (!next.isBlank()) detailQueue.addLast(next);
                            }
                        }
                    }
                    page.setStatus(CrawlPage.Status.SUCCEEDED);
                } catch (Exception exception) {
                    failures++;
                    page.setStatus(CrawlPage.Status.FAILED);
                    page.setErrorMessage(shortMessage(exception));
                }
                pageRepository.save(page);
                int totalFailures = priorFailures + failures
                        + (int) assetRepository.countByJobIdAndStatus(jobId, CrawlAsset.Status.FAILED);
                jobRepository.updateDownloadProgress(
                        jobId,
                        job.getPagesProcessed() + 1,
                        (int) assetRepository.countByJobIdAndStatus(jobId, CrawlAsset.Status.DOWNLOADED),
                        totalFailures);
                job.setPagesProcessed(job.getPagesProcessed() + 1);
            }
            if (stopped(jobId)) return;
            job = jobRepository.findById(jobId).orElseThrow();
            job.setPhase(CrawlJob.Phase.REVIEW);
            int totalFailures = priorFailures + failures
                    + (int) assetRepository.countByJobIdAndStatus(jobId, CrawlAsset.Status.FAILED);
            job.setFailCount(totalFailures);
            job.setStatus(totalFailures == 0 ? CrawlJob.Status.COMPLETED : CrawlJob.Status.PARTIAL);
            job.setFinishedAt(LocalDateTime.now());
            clearLease(job);
            jobRepository.save(job);
        } catch (Exception exception) {
            failJob(job, workerId, exception);
        }
    }

    private void recoverInterruptedItems(Long jobId) {
        for (CrawlPage page : pageRepository.findByJobIdAndStatusOrderById(
                jobId, CrawlPage.Status.RUNNING)) {
            page.setStatus(CrawlPage.Status.PENDING);
            page.setErrorMessage(null);
            pageRepository.save(page);
        }
        for (CrawlAsset asset : assetRepository.findByJobIdAndStatus(
                jobId, CrawlAsset.Status.PENDING)) {
            asset.setStatus(CrawlAsset.Status.FAILED);
            asset.setErrorMessage("上次执行中断，已重新处理来源页面");
            assetRepository.save(asset);
        }
    }

    private void downloadAsset(
            Long jobId, Long ownerId, CrawlPage page, CrawlRule rule, String imageUrl) {
        String normalized = SafeHttpFetcher.normalize(imageUrl);
        String urlHash = SafeHttpFetcher.urlHash(normalized);
        CrawlAsset asset = assetRepository.findByJobIdAndUrlHash(jobId, urlHash).orElse(null);
        if (asset == null) {
            asset = new CrawlAsset();
            asset.setJobId(jobId);
            asset.setPageId(page.getId());
            asset.setSourcePageUrl(page.getUrl());
            asset.setImageUrl(imageUrl);
            asset.setNormalizedUrl(normalized);
            asset.setUrlHash(urlHash);
            asset.setOriginalFilename(filename(URI.create(imageUrl)));
            asset = assetRepository.save(asset);
        }
        recordSource(asset, page);
        if (asset.getStatus() != CrawlAsset.Status.FAILED
                && asset.getStatus() != CrawlAsset.Status.PENDING) return;
        asset.setStatus(CrawlAsset.Status.PENDING);
        asset.setErrorMessage(null);
        asset = assetRepository.save(asset);
        var skip = imageSkipRepository.findByOwnerIdAndUrlHash(ownerId, urlHash).orElse(null);
        if (skip != null && normalized.equals(skip.getNormalizedUrl())) {
            skip.setSkipCount(skip.getSkipCount() + 1);
            skip.setLastSkippedAt(LocalDateTime.now());
            if (skip.getSourcePageUrl() == null) skip.setSourcePageUrl(page.getUrl());
            imageSkipRepository.save(skip);
            asset.setStatus(CrawlAsset.Status.SKIPPED);
            asset.setErrorMessage("已按永久跳过规则跳过");
            assetRepository.save(asset);
            return;
        }
        try {
            SafeHttpFetcher.FetchedResource resource = fetcher.fetch(
                    imageUrl, rule.getAllowedHosts(), rule.getMaxFileBytes(),
                    pageHostInterval(imageUrl, rule));
            if (!resource.contentType().startsWith("image/")) {
                throw new IllegalArgumentException("响应不是图片");
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(resource.bytes()));
            if (image == null && !"image/webp".equals(resource.contentType())) {
                throw new IllegalArgumentException("图片无法解码");
            }
            if (image != null) {
                long pixels = (long) image.getWidth() * image.getHeight();
                if (pixels > 100_000_000L) throw new IllegalArgumentException("图片像素超过限制");
                asset.setWidth(image.getWidth());
                asset.setHeight(image.getHeight());
                asset.setFileHashPhash(perceptualHashService.differenceHash(image));
                ByteArrayOutputStream thumbnail = new ByteArrayOutputStream();
                Thumbnails.of(image).size(400, 400).outputFormat("jpg")
                        .outputQuality(0.8).toOutputStream(thumbnail);
                asset.setThumbnailPath(stagingStorage.writeThumbnail(
                        jobId, asset.getId(), thumbnail.toByteArray()));
            }
            asset.setContentType(resource.contentType());
            asset.setFileSize((long) resource.bytes().length);
            asset.setFileHashMd5(md5(resource.bytes()));
            asset.setLocalPath(stagingStorage.write(
                    jobId, asset.getId(), resource.bytes(), extension(resource.contentType())));
            asset.setStatus(CrawlAsset.Status.DOWNLOADED);
        } catch (Exception exception) {
            asset.setStatus(CrawlAsset.Status.FAILED);
            asset.setErrorMessage(shortMessage(exception));
        }
        assetRepository.save(asset);
    }

    private void failJob(CrawlJob job, String workerId, Exception exception) {
        if (stopped(job.getId())) return;
        if (!queueService.renew(job.getId(), workerId)) return;
        log.warn("Crawl job {} failed: {}", job.getId(), exception.getMessage());
        job.setStatus(CrawlJob.Status.FAILED);
        job.setErrorMessage(shortMessage(exception));
        job.setFinishedAt(LocalDateTime.now());
        clearLease(job);
        jobRepository.save(job);
    }

    private void clearLease(CrawlJob job) {
        job.setLeaseOwner(null);
        job.setLeaseUntil(null);
    }

    private void requireHtml(String contentType) {
        if (!contentType.equals("text/html") && !contentType.equals("application/xhtml+xml")) {
            throw new IllegalArgumentException("页面响应不是 HTML");
        }
    }

    private String filename(URI uri) {
        String path = uri.getPath();
        String name = path == null || path.isBlank() ? "image" : path.substring(path.lastIndexOf('/') + 1);
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        return name.isBlank() ? "image" : name.substring(0, Math.min(name.length(), 240));
    }

    private String extension(String contentType) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }

    private String md5(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(bytes));
    }

    private long pageHostInterval(String url, CrawlRule rule) {
        URI start = URI.create(rule.getStartUrl());
        URI target = URI.create(url);
        return start.getHost() != null && start.getHost().equalsIgnoreCase(target.getHost())
                ? rule.getMinRequestIntervalMillis() : 0L;
    }

    private void recordSource(CrawlAsset asset, CrawlPage page) {
        if (assetSourceRepository.existsByAssetIdAndPageId(asset.getId(), page.getId())) return;
        CrawlAssetSource source = new CrawlAssetSource();
        source.setAssetId(asset.getId());
        source.setPageId(page.getId());
        source.setSourcePageUrl(page.getUrl());
        assetSourceRepository.save(source);
    }

    private String shortMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message.substring(0, Math.min(1000, message.length()));
    }

    private CrawlRule ruleSnapshot(CrawlJob job) {
        try {
            return objectMapper.readValue(job.getRuleSnapshot(), CrawlRule.class);
        } catch (Exception exception) {
            return ruleRepository.findById(job.getRuleId()).orElseThrow();
        }
    }

    private boolean stopped(Long jobId) {
        CrawlJob.Status status = jobRepository.findById(jobId).orElseThrow().getStatus();
        return status == CrawlJob.Status.PAUSED || status == CrawlJob.Status.CANCELLED;
    }
}
