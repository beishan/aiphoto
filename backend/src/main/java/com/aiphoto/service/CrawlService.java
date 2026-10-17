package com.aiphoto.service;

import com.aiphoto.entity.CrawlAsset;
import com.aiphoto.entity.CrawlJob;
import com.aiphoto.entity.CrawlPage;
import com.aiphoto.entity.CrawlRule;
import com.aiphoto.entity.CrawlSite;
import com.aiphoto.repository.CrawlAssetRepository;
import com.aiphoto.repository.CrawlJobRepository;
import com.aiphoto.repository.CrawlPageRepository;
import com.aiphoto.repository.CrawlRuleRepository;
import com.aiphoto.repository.CrawlSiteRepository;
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
    private final CrawlSiteRepository siteRepository;
    private final CrawlJobRepository jobRepository;
    private final CrawlPageRepository pageRepository;
    private final CrawlAssetRepository assetRepository;
    private final SafeHttpFetcher fetcher;
    private final ObjectMapper objectMapper;
    private final CrawlStagingStorageService stagingStorage;
    private final PhotoRepository photoRepository;
    private final CrawlerSettingsService crawlerSettingsService;

    public List<CrawlSite> listSites(Long ownerId) {
        return siteRepository.findByOwnerIdOrderByUpdatedAtDesc(ownerId);
    }

    @Transactional
    public CrawlSite saveSite(CrawlSite input, Long ownerId) {
        CrawlSite site = input.getId() == null ? new CrawlSite()
                : getSite(input.getId(), ownerId);
        String startUrl = requireText(input.getStartUrl(), "起始 URL");
        URI start = validateStartUrl(startUrl);
        String proxyMode = crawlerSettingsService.validateInspectionProxy(
                input.getInspectionProxyMode(), input.getInspectionProxyId());
        site.setInspectionProxyMode(proxyMode);
        site.setInspectionProxyId("SELECTED".equals(proxyMode) ? input.getInspectionProxyId() : null);
        site.setOwnerId(ownerId);
        site.setName(requireText(input.getName(), "网站名称"));
        site.setStartUrl(SafeHttpFetcher.normalize(startUrl));
        String blockedHosts = CrawlHostPolicy.normalizeBlacklist(input.getBlockedHosts());
        CrawlHostPolicy.requireNotBlocked(start, blockedHosts);
        site.setBlockedHosts(blockedHosts);
        List<String> discoveryUrls = validatedDiscoveryUrls(
                input.getDiscoveryUrls(), site.getStartUrl(), blockedHosts);
        site.setDiscoveryUrls(input.getDiscoveryUrls() == null || input.getDiscoveryUrls().isBlank()
                ? null : String.join("\n", discoveryUrls));
        site.setMaxListPages(clamp(input.getMaxListPages(), 1, 1000, 100));
        site.setMaxDetailPages(clamp(input.getMaxDetailPages(), 1, 20000, 1000));
        site.setMaxImages(clamp(input.getMaxImages(), 1, 50000, 5000));
        site.setMaxFileBytes(input.getMaxFileBytes() == null
                ? 20L * 1024 * 1024 : Math.max(1024, Math.min(input.getMaxFileBytes(), 100L * 1024 * 1024)));
        site.setMinRequestIntervalMillis(input.getMinRequestIntervalMillis() == null
                ? 1000L : Math.max(0L, Math.min(input.getMinRequestIntervalMillis(), 60_000L)));
        site.setScheduleEnabled(Boolean.TRUE.equals(input.getScheduleEnabled()));
        site.setDailyScanTime(input.getDailyScanTime() == null
                ? java.time.LocalTime.of(3, 0) : input.getDailyScanTime());
        return siteRepository.save(site);
    }

    public CrawlSite getSite(Long siteId, Long ownerId) {
        return siteRepository.findByIdAndOwnerId(siteId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("采集网站不存在"));
    }

    public List<CrawlRule> listRules(Long siteId, Long ownerId) {
        getSite(siteId, ownerId);
        return ruleRepository.findBySiteIdAndOwnerIdOrderByUpdatedAtDesc(siteId, ownerId);
    }

    @Transactional
    public CrawlRule saveRule(CrawlRule input, Long ownerId) {
        CrawlRule rule = input.getId() == null ? new CrawlRule()
                : ruleRepository.findByIdAndOwnerId(input.getId(), ownerId)
                        .orElseThrow(() -> new IllegalArgumentException("采集规则不存在"));
        CrawlSite site = getSite(input.getSiteId(), ownerId);
        if (rule.getId() != null && !java.util.Objects.equals(rule.getSiteId(), site.getId())) {
            throw new IllegalArgumentException("解析规则不能移动到其他网站");
        }
        boolean activate = Boolean.TRUE.equals(input.getEnabled());
        if (activate) ruleRepository.deactivateAll(site.getId());
        copyValidatedRule(input, rule, site, ownerId);
        rule.setEnabled(activate);
        return ruleRepository.save(rule);
    }

    public PreviewResult preview(CrawlRule input, Long ownerId) throws Exception {
        return preview(input, ownerId, null, null);
    }

    public PreviewResult preview(
            CrawlRule input, Long ownerId, String proxyMode, Long proxyId) throws Exception {
        CrawlRule rule = new CrawlRule();
        if (input.getId() != null) {
            getRule(input.getId(), ownerId);
            rule.setId(input.getId());
        }
        CrawlSite site = getSite(input.getSiteId(), ownerId);
        copyValidatedRule(input, rule, site, ownerId);
        var network = crawlerSettingsService.inspectionNetwork(
                proxyMode == null ? site.getInspectionProxyMode() : proxyMode,
                proxyMode == null ? site.getInspectionProxyId() : proxyId);
        SafeHttpFetcher.FetchedResource resource = fetcher.fetchForInspection(
                rule.getDiscoveryUrls().get(0), rule.getBlockedHosts(), MAX_HTML_BYTES,
                rule.getMinRequestIntervalMillis(), network);
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
            imageUrls = previewImages(rule, detailUrls, network);
        } catch (Exception exception) {
            imagePreviewError = exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage();
        }
        return new PreviewResult(rule, detailUrls, imageUrls, imagePreviewError);
    }

    private List<String> previewImages(CrawlRule rule, List<String> detailUrls,
            CrawlerSettingsService.NetworkSnapshot network) throws Exception {
        if (detailUrls.isEmpty()) return List.of();
        SafeHttpFetcher.FetchedResource resource = fetcher.fetchForInspection(
                detailUrls.get(0), rule.getBlockedHosts(), MAX_HTML_BYTES,
                rule.getMinRequestIntervalMillis(), network);
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

    public SiteInfo inspectSite(CrawlSite input, Long ownerId) throws Exception {
        if (input.getId() != null) getSite(input.getId(), ownerId);
        String startUrl = requireText(input.getStartUrl(), "起始 URL");
        URI start = validateStartUrl(startUrl);
        String hosts = CrawlHostPolicy.normalizeBlacklist(input.getBlockedHosts());
        CrawlHostPolicy.requireNotBlocked(start, hosts);
        var network = crawlerSettingsService.inspectionNetwork(
                input.getInspectionProxyMode(), input.getInspectionProxyId());
        long interval = input.getMinRequestIntervalMillis() == null ? 1000L
                : Math.max(0L, Math.min(input.getMinRequestIntervalMillis(), 60_000L));
        var resource = fetcher.fetchForInspection(startUrl, hosts, MAX_HTML_BYTES, interval, network);
        requireHtml(resource.contentType());
        Document document = Jsoup.parse(
                new String(resource.bytes(), StandardCharsets.UTF_8), resource.finalUri().toString());
        Element description = document.selectFirst("meta[name=description], meta[property=og:description]");
        return new SiteInfo(document.title(), description == null ? "" : description.attr("content"),
                resource.finalUri().toString());
    }

    public record SiteInfo(String title, String description, String finalUrl) {}

    private void copyValidatedRule(
            CrawlRule input, CrawlRule rule, CrawlSite site, Long ownerId) {
        validateStartUrl(site.getStartUrl());
        rule.setSiteId(site.getId());
        applySiteConfig(rule, site);
        rule.setEnabled(Boolean.TRUE.equals(input.getEnabled()));
        rule.setOwnerId(ownerId);
        rule.setName(requireText(input.getName(), "规则名称"));
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
    }

    private URI validateStartUrl(String startUrl) {
        URI start = URI.create(startUrl);
        if (start.getHost() == null || start.getUserInfo() != null
                || !("http".equalsIgnoreCase(start.getScheme())
                || "https".equalsIgnoreCase(start.getScheme()))) {
            throw new IllegalArgumentException("起始 URL 必须是 HTTP/HTTPS 地址");
        }
        if (start.getPort() != -1 && start.getPort() != 80 && start.getPort() != 443) {
            throw new IllegalArgumentException("起始 URL 只允许标准 HTTP/HTTPS 端口");
        }
        return start;
    }

    private List<String> validatedDiscoveryUrls(String value, String fallback, String blockedHosts) {
        if (value == null || value.isBlank()) return List.of(SafeHttpFetcher.normalize(fallback));
        if (value.length() > 50_000) throw new IllegalArgumentException("发现页配置过长");
        java.util.LinkedHashSet<String> urls = new java.util.LinkedHashSet<>();
        for (String line : value.split("\\R")) {
            String url = line.trim();
            if (url.isEmpty()) continue;
            if (url.length() > 2048) throw new IllegalArgumentException("发现页 URL 不能超过 2048 个字符");
            URI uri;
            try {
                uri = validateStartUrl(url);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("发现页 URL 无效：" + exception.getMessage());
            }
            try {
                CrawlHostPolicy.requireNotBlocked(uri, blockedHosts);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("发现页" + exception.getMessage());
            }
            urls.add(SafeHttpFetcher.normalize(url));
            if (urls.size() > 20) throw new IllegalArgumentException("最多配置 20 个发现页入口");
        }
        return List.copyOf(urls);
    }

    private void applySiteConfig(CrawlRule rule, CrawlSite site) {
        rule.setStartUrl(site.getStartUrl());
        rule.setDiscoveryUrls(validatedDiscoveryUrls(
                site.getDiscoveryUrls(), site.getStartUrl(), site.getBlockedHosts()));
        rule.setBlockedHosts(site.getBlockedHosts());
        rule.setMaxListPages(site.getMaxListPages());
        rule.setMaxDetailPages(site.getMaxDetailPages());
        rule.setMaxImages(site.getMaxImages());
        rule.setMaxFileBytes(site.getMaxFileBytes());
        rule.setMinRequestIntervalMillis(site.getMinRequestIntervalMillis());
    }

    @Transactional
    public CrawlJob createJob(Long ruleId, Long ownerId) {
        CrawlRule rule = getRule(ruleId, ownerId);
        if (!Boolean.TRUE.equals(rule.getEnabled())) {
            throw new IllegalStateException("只能使用当前生效的解析规则创建任务");
        }
        CrawlSite site = getSite(rule.getSiteId(), ownerId);
        applySiteConfig(rule, site);
        CrawlJob job = new CrawlJob();
        job.setOwnerId(ownerId);
        job.setSiteId(site.getId());
        job.setRuleId(ruleId);
        job.setName(site.getName() + " · " + rule.getName());
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

    public ImageProgress imageProgress(Long jobId, Long ownerId) {
        getJob(jobId, ownerId);
        List<CrawlAsset.Status> completedStatuses = List.of(
                CrawlAsset.Status.DOWNLOADED,
                CrawlAsset.Status.FAILED,
                CrawlAsset.Status.SKIPPED,
                CrawlAsset.Status.DELETED,
                CrawlAsset.Status.DUPLICATE,
                CrawlAsset.Status.IMPORTED);
        CrawlAsset runningAsset = assetRepository.findByJobIdAndStatusOrderByIdDesc(
                jobId, CrawlAsset.Status.RUNNING).stream().findFirst().orElse(null);
        return new ImageProgress(
                assetRepository.countByJobId(jobId),
                assetRepository.countByJobIdAndStatusIn(jobId, completedStatuses),
                assetRepository.countByJobIdAndStatus(jobId, CrawlAsset.Status.DOWNLOADED),
                runningAsset,
                runningAsset == null ? null : fetcher.activeProxyName(jobId));
    }

    @Transactional
    public CrawlJob prepareDownload(Long jobId, Long ownerId) {
        CrawlJob job = getJob(jobId, ownerId);
        if (job.getScanPageId() != null || job.getPhase() != CrawlJob.Phase.AWAITING_CONFIRMATION
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
        boolean emptyCompletedDownload = job.getPhase() == CrawlJob.Phase.REVIEW
                && job.getStatus() == CrawlJob.Status.COMPLETED
                && assetRepository.countByJobId(jobId) == 0;
        if (job.getStatus() == CrawlJob.Status.RUNNING || job.getStatus() == CrawlJob.Status.QUEUED
                || job.getStatus() == CrawlJob.Status.CANCELLED || job.getPhase() == CrawlJob.Phase.REVIEW
                && job.getStatus() == CrawlJob.Status.COMPLETED && !emptyCompletedDownload) {
            throw new IllegalStateException("当前任务不能继续或重试");
        }
        if (job.getPhase() == CrawlJob.Phase.DOWNLOAD || job.getPhase() == CrawlJob.Phase.REVIEW) {
            refreshDownloadRuleSnapshot(job);
            if (emptyCompletedDownload) {
                for (CrawlPage page : pageRepository.findByJobIdAndStatusAndIncludedTrueOrderById(
                        jobId, CrawlPage.Status.SUCCEEDED)) {
                    page.setStatus(CrawlPage.Status.PENDING);
                    page.setErrorMessage(null);
                }
            }
            for (CrawlPage page : pageRepository.pagesWithFailedAssets(jobId)) {
                page.setStatus(CrawlPage.Status.PENDING);
                page.setErrorMessage(null);
            }
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

    private void refreshDownloadRuleSnapshot(CrawlJob job) {
        if (job.getRuleId() == null) return;
        CrawlRule rule = getRule(job.getRuleId(), job.getOwnerId());
        CrawlSite site = getSite(rule.getSiteId(), job.getOwnerId());
        applySiteConfig(rule, site);
        try {
            job.setRuleSnapshot(objectMapper.writeValueAsString(rule));
        } catch (Exception exception) {
            throw new IllegalStateException("无法更新任务规则快照", exception);
        }
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
                : pageRepository.searchByJobId(
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
            boolean exactDuplicates, boolean similarOnly, Long pageId, Pageable pageable) {
        getJob(jobId, ownerId);
        if (exactDuplicates && similarOnly) {
            throw new IllegalArgumentException("精确重复和相似图片筛选不能同时启用");
        }
        if (pageId != null) {
            pageRepository.findByIdAndJobId(pageId, jobId)
                    .orElseThrow(() -> new IllegalArgumentException("图片页不存在"));
            if (exactDuplicates || similarOnly) {
                throw new IllegalArgumentException("按来源页查看时不能叠加重复图片筛选");
            }
        }
        Page<CrawlAsset> result = pageId != null
                ? status == null
                        ? assetRepository.findByJobIdAndSourcePageId(jobId, pageId, pageable)
                        : assetRepository.findByJobIdAndSourcePageIdAndStatus(
                                jobId, pageId, status, pageable)
                : similarOnly
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
        // Keep discovered URLs available when an individual collection task is removed.
        pageRepository.clearCollectionJob(jobId);
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
    public record ImageProgress(
            long total, long processed, long downloaded, CrawlAsset runningAsset,
            String currentProxyName) {}
}
