package com.aiphoto.service;

import com.aiphoto.entity.*;
import com.aiphoto.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlWorkflowService {
    private final CrawlScanPageRepository scanRepository;
    private final CrawlPageRepository pageRepository;
    private final CrawlJobRepository jobRepository;
    private final CrawlRuleRepository ruleRepository;
    private final CrawlService crawlService;
    private final ObjectMapper mapper;
    private final CrawlSeenPageRepository seenPageRepository;

    public List<CrawlScanPage> scans(Long ownerId) {
        return scanRepository.findByOwnerIdOrderById(ownerId);
    }

    @Transactional
    public CrawlScanPage saveScan(Long id, CrawlScanPage input, Long ownerId) {
        CrawlSite site = crawlService.getSite(input.getSiteId(), ownerId);
        CrawlScanPage page = id == null ? new CrawlScanPage() : ownedScan(id, ownerId);
        if (id != null && !page.getSiteId().equals(site.getId())) {
            throw new IllegalArgumentException("扫描页不能移动到其他网站");
        }
        String name = input.getName() == null ? "" : input.getName().trim();
        if (name.isEmpty() || name.length() > 100) throw new IllegalArgumentException("扫描页名称须为 1–100 字");
        String url = input.getUrl() == null ? "" : input.getUrl().trim();
        URI uri = URI.create(url);
        if (url.length() > 2048 || uri.getHost() == null || uri.getUserInfo() != null
                || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443)) {
            throw new IllegalArgumentException("扫描页必须是标准端口的 HTTP/HTTPS 地址");
        }
        CrawlHostPolicy.requireNotBlocked(uri, site.getBlockedHosts());
        int interval = input.getScanIntervalMinutes() == null ? 360 : input.getScanIntervalMinutes();
        int maxPages = input.getMaxPages() == null ? 100 : input.getMaxPages();
        if (interval < 5 || interval > 10080 || maxPages < 1 || maxPages > 1000) {
            throw new IllegalArgumentException("周期须为 5–10080 分钟，页数须为 1–1000");
        }
        page.setOwnerId(ownerId);
        page.setSiteId(site.getId());
        page.setName(name);
        page.setUrl(SafeHttpFetcher.normalize(url));
        page.setAutoScanEnabled(Boolean.TRUE.equals(input.getAutoScanEnabled()));
        page.setAutoCollectEnabled(Boolean.TRUE.equals(input.getAutoCollectEnabled()));
        int autoCollectPriority = input.getAutoCollectPriority() == null
                ? page.getAutoCollectPriority() == null
                    ? CrawlJob.DOWNLOAD_PRIORITY : page.getAutoCollectPriority()
                : input.getAutoCollectPriority();
        if (autoCollectPriority < 0 || autoCollectPriority > 100) {
            throw new IllegalArgumentException("任务优先级必须在 0 至 100 之间");
        }
        page.setAutoCollectPriority(autoCollectPriority);
        page.setScanIntervalMinutes(interval);
        page.setMaxPages(maxPages);
        return scanRepository.save(page);
    }

    private CrawlScanPage ownedScan(Long id, Long ownerId) {
        return scanRepository.lockOwned(id, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("扫描页不存在"));
    }

    @Transactional
    public void deleteScan(Long id, Long ownerId) {
        CrawlScanPage page = ownedScan(id, ownerId);
        if (jobRepository.existsByScanPageIdAndStatusIn(id,
                List.of(CrawlJob.Status.RUNNING, CrawlJob.Status.QUEUED, CrawlJob.Status.PAUSED))) {
            throw new IllegalStateException("请先完成或取消该扫描页的任务");
        }
        scanRepository.delete(page);
    }

    @Transactional
    public CrawlJob scan(Long id, Long ownerId, boolean scheduled) {
        CrawlScanPage page = ownedScan(id, ownerId);
        LocalDateTime now = LocalDateTime.now();
        if (scheduled && (!Boolean.TRUE.equals(page.getAutoScanEnabled())
                || page.getLastScanAt() != null && page.getLastScanAt()
                    .plusMinutes(page.getScanIntervalMinutes()).isAfter(now))) return null;
        if (jobRepository.existsByScanPageIdAndStatusIn(id,
                List.of(CrawlJob.Status.RUNNING, CrawlJob.Status.QUEUED, CrawlJob.Status.PAUSED))) {
            if (scheduled) return null;
            throw new IllegalStateException("该扫描页已有待执行、运行中或暂停的扫描任务");
        }
        CrawlRule active = ruleRepository.findBySiteIdAndOwnerIdAndEnabledTrue(page.getSiteId(), ownerId)
                .orElseThrow(() -> new IllegalStateException("请先配置生效的解析规则"));
        CrawlJob job = crawlService.createJob(active.getId(), ownerId);
        CrawlRule snapshot = readRule(job);
        snapshot.setStartUrl(page.getUrl());
        snapshot.setDiscoveryUrls(List.of(page.getUrl()));
        snapshot.setMaxListPages(page.getMaxPages());
        job.setRuleSnapshot(writeRule(snapshot));
        job.setScanPageId(page.getId());
        job.setName("扫描 · " + page.getName());
        page.setLastScanAt(now);
        scanRepository.save(page);
        return jobRepository.save(job);
    }

    @Transactional
    public int autoCollectDiscoveredPages(Long scanJobId, Long ownerId) {
        CrawlJob source = crawlService.getJob(scanJobId, ownerId);
        if (source.getScanPageId() == null || source.getPhase() != CrawlJob.Phase.DISCOVERY
                || !List.of(CrawlJob.Status.COMPLETED, CrawlJob.Status.PARTIAL).contains(source.getStatus())) {
            return 0;
        }
        CrawlScanPage scanPage = ownedScan(source.getScanPageId(), ownerId);
        if (!Boolean.TRUE.equals(scanPage.getAutoCollectEnabled())) return 0;

        int created = 0;
        for (CrawlPage page : pageRepository.findByJobIdAndStatusAndCollectionJobIdIsNullOrderById(
                scanJobId, CrawlPage.Status.PENDING)) {
            try {
                CrawlJob collection = collect(page.getId(), ownerId);
                collection.setPriority(scanPage.getAutoCollectPriority());
                jobRepository.save(collection);
                created++;
            } catch (RuntimeException exception) {
                log.warn("Could not auto-create collection task for discovered page {}: {}",
                        page.getId(), exception.getMessage());
            }
        }
        return created;
    }

    @Transactional
    public CrawlJob collect(Long pageId, Long ownerId) {
        CrawlPage page = pageRepository.lockById(pageId)
                .orElseThrow(() -> new IllegalArgumentException("待处理页面不存在"));
        CrawlJob source = crawlService.getJob(page.getJobId(), ownerId);
        if (source.getSourcePageId() != null) throw new IllegalStateException("请选择扫描发现的页面");
        if (page.getCollectionJobId() != null) return crawlService.getJob(page.getCollectionJobId(), ownerId);
        if (page.getStatus() != CrawlPage.Status.PENDING
                || source.getPhase() != CrawlJob.Phase.DISCOVERY
                    && source.getPhase() != CrawlJob.Phase.AWAITING_CONFIRMATION) {
            throw new IllegalStateException("该页面已进入原采集任务，请查看原任务");
        }
        CrawlRule active = ruleRepository.findBySiteIdAndOwnerIdAndEnabledTrue(source.getSiteId(), ownerId)
                .orElseThrow(() -> new IllegalStateException("请先配置生效的解析规则"));
        CrawlJob job = crawlService.createJob(active.getId(), ownerId);
        job.setSourcePageId(page.getId());
        job.setPhase(CrawlJob.Phase.DOWNLOAD);
        job.setPagesFound(1);
        String name = "采集 · " + (page.getName() == null || page.getName().isBlank() ? page.getUrl() : page.getName());
        job.setName(name.substring(0, name.offsetByCodePoints(0, Math.min(name.codePointCount(0, name.length()), 200))));
        job = jobRepository.save(job);
        CrawlPage target = new CrawlPage();
        target.setJobId(job.getId());
        target.setUrl(page.getUrl());
        target.setNormalizedUrl(page.getNormalizedUrl());
        target.setUrlHash(page.getUrlHash());
        target.setName(page.getName());
        pageRepository.save(target);
        page.setCollectionJobId(job.getId());
        pageRepository.save(page);
        return job;
    }

    @Transactional
    public void deleteDiscoveredPage(Long pageId, Long ownerId) {
        deleteDiscoveredPages(List.of(pageId), ownerId);
    }

    @Transactional
    public void deleteDiscoveredPages(List<Long> pageIds, Long ownerId) {
        if (pageIds == null || pageIds.isEmpty()) throw new IllegalArgumentException("请选择待删除的页面");
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(pageIds);
        if (uniqueIds.contains(null)) throw new IllegalArgumentException("页面 ID 无效");
        if (uniqueIds.size() > 200) throw new IllegalArgumentException("每次最多删除 200 个页面");

        List<CrawlPage> pages = new ArrayList<>();
        for (Long id : uniqueIds) {
            CrawlPage page = pageRepository.lockById(id)
                    .orElseThrow(() -> new IllegalArgumentException("待处理页面不存在"));
            CrawlJob source = crawlService.getJob(page.getJobId(), ownerId);
            if (source.getSourcePageId() != null) throw new IllegalArgumentException("只能删除扫描发现的页面");
            if ((source.getPhase() == CrawlJob.Phase.DOWNLOAD || source.getPhase() == CrawlJob.Phase.REVIEW)
                    && List.of(CrawlJob.Status.QUEUED, CrawlJob.Status.RUNNING, CrawlJob.Status.PAUSED)
                            .contains(source.getStatus())) {
                throw new IllegalStateException("请先完成或取消原采集任务");
            }
            if (page.getCollectionJobId() != null) {
                CrawlJob collection = crawlService.getJob(page.getCollectionJobId(), ownerId);
                if (List.of(CrawlJob.Status.QUEUED, CrawlJob.Status.RUNNING, CrawlJob.Status.PAUSED)
                        .contains(collection.getStatus())) {
                    throw new IllegalStateException("请先完成或取消正在执行的采集任务");
                }
            }
            pages.add(page);
        }

        for (CrawlPage page : pages) {
            seenPageRepository.deleteBySiteIdAndUrlHash(
                    crawlService.getJob(page.getJobId(), ownerId).getSiteId(), page.getUrlHash());
            pageRepository.delete(page);
        }
    }

    public Page<DiscoveredPage> pages(
            Long ownerId, Long siteId, String query, boolean pending, boolean collected, Pageable pageable) {
        if (siteId != null) crawlService.getSite(siteId, ownerId);
        return pageRepository.discovered(
                ownerId, siteId, query == null ? "" : query.trim(), pending, collected, pageable)
                .map(page -> {
                    CrawlJob source = crawlService.getJob(page.getJobId(), ownerId);
                    CrawlJob collection = page.getCollectionJobId() == null ? null
                            : crawlService.getJob(page.getCollectionJobId(), ownerId);
                    boolean legacy = source.getPhase() == CrawlJob.Phase.DOWNLOAD
                            || source.getPhase() == CrawlJob.Phase.REVIEW;
                    CrawlJob task = collection != null ? collection : legacy ? source : null;
                    return new DiscoveredPage(page.getId(), source.getId(), source.getSiteId(),
                            source.getScanPageId(), page.getName(), page.getUrl(), page.getCreatedAt(),
                            task == null ? null : task.getId(), task == null ? "PENDING" : task.getStatus().name(),
                            task == null && page.getStatus() == CrawlPage.Status.PENDING);
                });
    }

    private CrawlRule readRule(CrawlJob job) {
        try { return mapper.readValue(job.getRuleSnapshot(), CrawlRule.class); }
        catch (Exception e) { throw new IllegalStateException("读取规则快照失败", e); }
    }

    private String writeRule(CrawlRule rule) {
        try { return mapper.writeValueAsString(rule); }
        catch (Exception e) { throw new IllegalStateException("保存规则快照失败", e); }
    }

    public record DiscoveredPage(Long id, Long scanJobId, Long siteId, Long scanPageId,
            String name, String url, LocalDateTime createdAt, Long collectionJobId, String status, boolean collectable) {}
}
