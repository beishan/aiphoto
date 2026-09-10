package com.aiphoto.service;

import com.aiphoto.entity.CrawlAsset;
import com.aiphoto.entity.CrawlJob;
import com.aiphoto.entity.CrawlPage;
import com.aiphoto.entity.CrawlRule;
import com.aiphoto.repository.CrawlAssetRepository;
import com.aiphoto.repository.CrawlJobRepository;
import com.aiphoto.repository.CrawlPageRepository;
import com.aiphoto.repository.CrawlRuleRepository;
import com.aiphoto.repository.PhotoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CrawlService {

    private static final long MAX_HTML_BYTES = 5L * 1024 * 1024;
    private final CrawlRuleRepository ruleRepository;
    private final CrawlJobRepository jobRepository;
    private final CrawlPageRepository pageRepository;
    private final CrawlAssetRepository assetRepository;
    private final SafeHttpFetcher fetcher;
    private final ObjectMapper objectMapper;
    private final CrawlStagingStorageService stagingStorage;
    private final PhotoRepository photoRepository;

    public List<CrawlRule> listRules(Long ownerId) {
        return ruleRepository.findByOwnerIdOrderByUpdatedAtDesc(ownerId);
    }

    @Transactional
    public CrawlRule saveRule(CrawlRule input, Long ownerId) {
        CrawlRule rule = input.getId() == null ? new CrawlRule()
                : ruleRepository.findByIdAndOwnerId(input.getId(), ownerId)
                        .orElseThrow(() -> new IllegalArgumentException("采集规则不存在"));
        copyValidatedRule(input, rule, ownerId);
        return ruleRepository.save(rule);
    }

    public PreviewResult preview(CrawlRule input, Long ownerId) throws Exception {
        CrawlRule rule = new CrawlRule();
        if (input.getId() != null) {
            getRule(input.getId(), ownerId);
            rule.setId(input.getId());
        }
        copyValidatedRule(input, rule, ownerId);
        SafeHttpFetcher.FetchedResource resource = fetcher.fetch(
                rule.getStartUrl(), rule.getAllowedHosts(), MAX_HTML_BYTES);
        requireHtml(resource.contentType());
        Document document = Jsoup.parse(
                new String(resource.bytes(), StandardCharsets.UTF_8), resource.finalUri().toString());
        List<String> urls = new ArrayList<>();
        for (Element element : document.select(rule.getDetailSelector())) {
            String url = element.absUrl("href");
            if (url.isBlank()) continue;
            String normalized = SafeHttpFetcher.normalize(url);
            if (CrawlRuleMatcher.matches(
                    normalized, rule.getDetailUrlIncludes(), rule.getDetailUrlExcludes())
                    && urls.size() < 20) {
                urls.add(normalized);
            }
        }
        List<String> detailUrls = urls.stream().distinct().toList();
        List<String> imageUrls = List.of();
        String imagePreviewError = null;
        try {
            imageUrls = previewImages(rule, detailUrls);
        } catch (Exception exception) {
            imagePreviewError = exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage();
        }
        return new PreviewResult(rule, detailUrls, imageUrls, imagePreviewError);
    }

    private List<String> previewImages(CrawlRule rule, List<String> detailUrls) throws Exception {
        if (detailUrls.isEmpty()) return List.of();
        SafeHttpFetcher.FetchedResource resource = fetcher.fetch(
                detailUrls.get(0), rule.getAllowedHosts(), MAX_HTML_BYTES);
        requireHtml(resource.contentType());
        Document document = Jsoup.parse(
                new String(resource.bytes(), StandardCharsets.UTF_8), resource.finalUri().toString());
        List<String> images = new ArrayList<>();
        for (Element element : document.select(rule.getImageSelector())) {
            String url = CrawlRuleMatcher.extractImageUrl(
                    element, rule.getImageAttributes(), resource.finalUri());
            if (url == null) continue;
            String normalized = SafeHttpFetcher.normalize(url);
            if (CrawlRuleMatcher.matches(
                    normalized, rule.getImageUrlIncludes(), rule.getImageUrlExcludes())) {
                images.add(normalized);
            }
            if (images.size() >= 20) break;
        }
        return images.stream().distinct().toList();
    }

    private void copyValidatedRule(CrawlRule input, CrawlRule rule, Long ownerId) {
        String startUrl = requireText(input.getStartUrl(), "起始 URL");
        URI start = URI.create(startUrl);
        if (start.getHost() == null || start.getUserInfo() != null
                || !("http".equalsIgnoreCase(start.getScheme())
                || "https".equalsIgnoreCase(start.getScheme()))) {
            throw new IllegalArgumentException("起始 URL 必须是 HTTP/HTTPS 地址");
        }
        if (start.getPort() != -1 && start.getPort() != 80 && start.getPort() != 443) {
            throw new IllegalArgumentException("起始 URL 只允许标准 HTTP/HTTPS 端口");
        }
        rule.setOwnerId(ownerId);
        rule.setName(requireText(input.getName(), "规则名称"));
        rule.setStartUrl(SafeHttpFetcher.normalize(startUrl));
        rule.setDetailSelector(requireText(input.getDetailSelector(), "图片页选择器"));
        rule.setDetailUrlIncludes(cleanRules(input.getDetailUrlIncludes()));
        rule.setDetailUrlExcludes(cleanRules(input.getDetailUrlExcludes()));
        rule.setNextSelector(input.getNextSelector() == null ? "" : input.getNextSelector().trim());
        rule.setImageSelector(input.getImageSelector() == null || input.getImageSelector().isBlank()
                ? "img" : input.getImageSelector().trim());
        rule.setImageAttributes(input.getImageAttributes() == null || input.getImageAttributes().isBlank()
                ? "data-original,data-src,srcset,src" : input.getImageAttributes().trim());
        rule.setImageUrlIncludes(cleanRules(input.getImageUrlIncludes()));
        rule.setImageUrlExcludes(cleanRules(input.getImageUrlExcludes()));
        rule.setDetailNextSelector(input.getDetailNextSelector() == null
                ? "" : input.getDetailNextSelector().trim());
        rule.setMaxPagesPerDetail(clamp(input.getMaxPagesPerDetail(), 1, 100, 20));
        String allowedHosts = input.getAllowedHosts() == null || input.getAllowedHosts().isBlank()
                ? start.getHost().toLowerCase() : input.getAllowedHosts().trim().toLowerCase();
        boolean startAllowed = java.util.Arrays.stream(allowedHosts.split(","))
                .map(String::trim).anyMatch(start.getHost()::equalsIgnoreCase);
        if (!startAllowed) throw new IllegalArgumentException("允许域名必须包含起始网站域名");
        rule.setAllowedHosts(allowedHosts);
        rule.setMaxListPages(clamp(input.getMaxListPages(), 1, 1000, 100));
        rule.setMaxDetailPages(clamp(input.getMaxDetailPages(), 1, 20000, 1000));
        rule.setMaxImages(clamp(input.getMaxImages(), 1, 50000, 5000));
        rule.setMaxFileBytes(input.getMaxFileBytes() == null
                ? 20L * 1024 * 1024 : Math.max(1024, Math.min(input.getMaxFileBytes(), 100L * 1024 * 1024)));
    }

    @Transactional
    public CrawlJob createJob(Long ruleId, Long ownerId) {
        CrawlRule rule = getRule(ruleId, ownerId);
        CrawlJob job = new CrawlJob();
        job.setOwnerId(ownerId);
        job.setRuleId(ruleId);
        job.setName(rule.getName());
        try {
            job.setRuleSnapshot(objectMapper.writeValueAsString(rule));
        } catch (Exception exception) {
            throw new IllegalStateException("无法保存规则快照", exception);
        }
        return jobRepository.save(job);
    }

    public List<CrawlJob> listJobs(Long ownerId) {
        return jobRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    public CrawlJob getJob(Long jobId, Long ownerId) {
        return jobRepository.findByIdAndOwnerId(jobId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("采集任务不存在"));
    }

    @Transactional
    public CrawlJob prepareDownload(Long jobId, Long ownerId) {
        CrawlJob job = getJob(jobId, ownerId);
        if (job.getPhase() != CrawlJob.Phase.AWAITING_CONFIRMATION
                || job.getStatus() == CrawlJob.Status.RUNNING
                || pageRepository.countByJobIdAndIncludedTrue(jobId) == 0) {
            throw new IllegalStateException("当前任务不能开始下载");
        }
        job.setPhase(CrawlJob.Phase.DOWNLOAD);
        job.setStatus(CrawlJob.Status.QUEUED);
        job.setFinishedAt(null);
        return jobRepository.save(job);
    }

    @Transactional
    public CrawlJob pause(Long jobId, Long ownerId) {
        CrawlJob job = getJob(jobId, ownerId);
        if (job.getStatus() != CrawlJob.Status.RUNNING && job.getStatus() != CrawlJob.Status.QUEUED) {
            throw new IllegalStateException("当前任务不能暂停");
        }
        job.setStatus(CrawlJob.Status.PAUSED);
        job.setLeaseOwner(null);
        job.setLeaseUntil(null);
        return jobRepository.save(job);
    }

    @Transactional
    public CrawlJob cancel(Long jobId, Long ownerId) {
        CrawlJob job = getJob(jobId, ownerId);
        if (job.getStatus() == CrawlJob.Status.COMPLETED || job.getStatus() == CrawlJob.Status.CANCELLED) {
            throw new IllegalStateException("当前任务不能取消");
        }
        job.setStatus(CrawlJob.Status.CANCELLED);
        job.setFinishedAt(java.time.LocalDateTime.now());
        job.setLeaseOwner(null);
        job.setLeaseUntil(null);
        return jobRepository.save(job);
    }

    @Transactional
    public CrawlJob prepareResumeOrRetry(Long jobId, Long ownerId) {
        CrawlJob job = getJob(jobId, ownerId);
        if (job.getStatus() == CrawlJob.Status.RUNNING || job.getStatus() == CrawlJob.Status.QUEUED
                || job.getStatus() == CrawlJob.Status.CANCELLED || job.getPhase() == CrawlJob.Phase.REVIEW
                && job.getStatus() == CrawlJob.Status.COMPLETED) {
            throw new IllegalStateException("当前任务不能继续或重试");
        }
        if (job.getPhase() == CrawlJob.Phase.DOWNLOAD || job.getPhase() == CrawlJob.Phase.REVIEW) {
            for (CrawlPage page : pageRepository.findByJobIdAndStatusAndIncludedTrueOrderById(
                    jobId, CrawlPage.Status.FAILED)) {
                page.setStatus(CrawlPage.Status.PENDING);
                page.setErrorMessage(null);
            }
            job.setPhase(CrawlJob.Phase.DOWNLOAD);
        } else {
            job.setPhase(CrawlJob.Phase.DISCOVERY);
        }
        job.setStatus(CrawlJob.Status.QUEUED);
        job.setLeaseOwner(null);
        job.setLeaseUntil(null);
        job.setErrorMessage(null);
        job.setFinishedAt(null);
        return jobRepository.save(job);
    }

    public CrawlRule getRule(Long ruleId, Long ownerId) {
        return ruleRepository.findByIdAndOwnerId(ruleId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("采集规则不存在"));
    }

    public PageResult listPages(
            Long jobId, Long ownerId, String query, Pageable pageable) {
        getJob(jobId, ownerId);
        String normalizedQuery = query == null ? "" : query.trim();
        Page<CrawlPage> result = normalizedQuery.isEmpty()
                ? pageRepository.findByJobIdOrderById(jobId, pageable)
                : pageRepository.findByJobIdAndUrlContainingIgnoreCaseOrderById(
                        jobId, normalizedQuery, pageable);
        return new PageResult(
                result.getContent(), result.getTotalElements(), result.getTotalPages(),
                result.getSize(), result.getNumber(),
                pageRepository.countByJobIdAndIncludedTrue(jobId));
    }

    @Transactional
    public PageSelectionResult setPagesIncluded(
            Long jobId, Long ownerId, List<Long> ids, boolean included) {
        CrawlJob job = getJob(jobId, ownerId);
        if (job.getPhase() != CrawlJob.Phase.AWAITING_CONFIRMATION
                || job.getStatus() == CrawlJob.Status.RUNNING
                || job.getStatus() == CrawlJob.Status.QUEUED) {
            throw new IllegalStateException("只有等待确认的任务可以修改图片页清单");
        }
        int changed = 0;
        List<Long> safeIds = ids == null ? List.of() : ids.stream()
                .filter(java.util.Objects::nonNull).distinct().toList();
        for (CrawlPage page : pageRepository.findByIdInAndJobId(safeIds, jobId)) {
            if (!java.util.Objects.equals(page.getIncluded(), included)) {
                page.setIncluded(included);
                changed++;
            }
        }
        pageRepository.flush();
        return new PageSelectionResult(
                changed, pageRepository.countByJobIdAndIncludedTrue(jobId));
    }

    public Page<CrawlAsset> listAssets(
            Long jobId, Long ownerId, CrawlAsset.Status status,
            boolean exactDuplicates, boolean similarOnly, Pageable pageable) {
        getJob(jobId, ownerId);
        if (exactDuplicates && similarOnly) {
            throw new IllegalArgumentException("精确重复和相似图片筛选不能同时启用");
        }
        Page<CrawlAsset> result = similarOnly
                ? assetRepository.findByJobIdAndStatusAndSimilarityGroupIdIsNotNullOrderByIdDesc(
                        jobId, CrawlAsset.Status.DOWNLOADED, pageable)
                : exactDuplicates
                ? assetRepository.findExactDuplicates(jobId, CrawlAsset.Status.DELETED, pageable)
                : status == null
                        ? assetRepository.findByJobIdOrderByIdDesc(jobId, pageable)
                        : assetRepository.findByJobIdAndStatusOrderByIdDesc(jobId, status, pageable);
        enrichDuplicateState(jobId, result.getContent());
        return result;
    }

    private void enrichDuplicateState(Long jobId, List<CrawlAsset> assets) {
        List<String> hashes = assets.stream().map(CrawlAsset::getFileHashMd5)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (hashes.isEmpty()) return;
        Map<String, Long> counts = assetRepository.countHashes(
                        jobId, CrawlAsset.Status.DELETED, hashes).stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));
        Map<String, com.aiphoto.entity.Photo> photos = photoRepository
                .findByFileHashMd5InIncludingTrash(hashes).stream()
                .collect(Collectors.toMap(
                        com.aiphoto.entity.Photo::getFileHashMd5,
                        Function.identity(),
                        (first, ignored) -> first));
        for (CrawlAsset asset : assets) {
            if (asset.getFileHashMd5() == null) continue;
            asset.setExactDuplicateCount(counts.getOrDefault(asset.getFileHashMd5(), 0L));
            var photo = photos.get(asset.getFileHashMd5());
            asset.setLibraryDuplicate(photo != null && photo.getDeletedAt() == null);
            asset.setLibraryTrashDuplicate(photo != null && photo.getDeletedAt() != null);
        }
    }

    public CrawlAsset getAsset(Long assetId, Long ownerId) {
        CrawlAsset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("采集图片不存在"));
        getJob(asset.getJobId(), ownerId);
        return asset;
    }

    @Transactional
    public int setAssetDeleted(List<Long> ids, Long jobId, Long ownerId, boolean deleted) {
        getJob(jobId, ownerId);
        int changed = 0;
        for (CrawlAsset asset : assetRepository.findByIdInAndJobId(ids, jobId)) {
            if (deleted && asset.getStatus() != CrawlAsset.Status.DOWNLOADED) continue;
            if (!deleted && asset.getStatus() != CrawlAsset.Status.DELETED) continue;
            asset.setStatus(deleted ? CrawlAsset.Status.DELETED : CrawlAsset.Status.DOWNLOADED);
            changed++;
        }
        if (changed > 0) assetRepository.clearSimilarityGroups(jobId);
        return changed;
    }

    @Transactional
    public int batchEditAssets(List<Long> ids, Long jobId, Long ownerId, String note) {
        getJob(jobId, ownerId);
        int changed = 0;
        for (CrawlAsset asset : assetRepository.findByIdInAndJobId(ids, jobId)) {
            if (asset.getStatus() == CrawlAsset.Status.IMPORTED) continue;
            asset.setNote(note == null ? null : note.trim());
            changed++;
        }
        return changed;
    }

    public PurgeResult purgeJob(Long jobId, Long ownerId) {
        CrawlJob job = getJob(jobId, ownerId);
        if (job.getStatus() == CrawlJob.Status.RUNNING || job.getStatus() == CrawlJob.Status.QUEUED) {
            throw new IllegalStateException("运行中的任务不能清理");
        }
        int fileFail = 0;
        for (CrawlAsset asset : assetRepository.findByJobIdOrderByIdDesc(jobId)) {
            try {
                stagingStorage.delete(asset.getLocalPath());
                stagingStorage.delete(asset.getThumbnailPath());
            } catch (Exception exception) {
                fileFail++;
            }
        }
        if (fileFail > 0) return new PurgeResult(0, fileFail);
        jobRepository.delete(job);
        return new PurgeResult(1, 0);
    }

    private String requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
        return value.trim();
    }

    private String cleanRules(String value) {
        return value == null ? "" : value.trim();
    }

    private int clamp(Integer value, int min, int max, int fallback) {
        return value == null ? fallback : Math.max(min, Math.min(max, value));
    }

    private void requireHtml(String contentType) {
        if (!contentType.equals("text/html") && !contentType.equals("application/xhtml+xml")) {
            throw new IllegalArgumentException("页面响应不是 HTML");
        }
    }

    public record PreviewResult(
            CrawlRule rule, List<String> detailUrls, List<String> imageUrls,
            String imagePreviewError) {}
    public record PageResult(
            List<CrawlPage> content, long totalElements, int totalPages,
            int size, int number, long includedElements) {}
    public record PageSelectionResult(int success, long included) {}
    public record PurgeResult(int success, int fileFail) {}
}
