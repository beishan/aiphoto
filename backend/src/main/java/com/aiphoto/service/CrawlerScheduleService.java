package com.aiphoto.service;

import com.aiphoto.async.CrawlWorker;
import com.aiphoto.entity.CrawlJob;
import com.aiphoto.entity.CrawlRule;
import com.aiphoto.entity.CrawlSite;
import com.aiphoto.repository.CrawlJobRepository;
import com.aiphoto.repository.CrawlRuleRepository;
import com.aiphoto.repository.CrawlSiteRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerScheduleService {

    private static final List<CrawlJob.Status> ACTIVE_STATUSES =
            List.of(CrawlJob.Status.QUEUED, CrawlJob.Status.RUNNING);

    private final CrawlSiteRepository siteRepository;
    private final CrawlRuleRepository ruleRepository;
    private final CrawlJobRepository jobRepository;
    private final CrawlService crawlService;
    private final CrawlWorker crawlWorker;

    @Scheduled(fixedDelayString = "${app.crawler.schedule-check-interval-ms:60000}")
    public void dispatchDailyScans() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalTime currentTime = now.toLocalTime();
        for (CrawlSite site : siteRepository.findByScheduleEnabledTrue()) {
            boolean claimed = false;
            boolean jobCreated = false;
            try {
                LocalTime scanTime = site.getDailyScanTime() == null
                        ? LocalTime.of(3, 0) : site.getDailyScanTime();
                if (currentTime.isBefore(scanTime)
                        || today.equals(site.getLastScheduledScanDate())
                        || jobRepository.existsBySiteIdAndStatusIn(site.getId(), ACTIVE_STATUSES)) {
                    continue;
                }
                CrawlRule rule = ruleRepository
                        .findBySiteIdAndOwnerIdAndEnabledTrue(site.getId(), site.getOwnerId())
                        .orElse(null);
                if (rule == null || siteRepository.claimDailyScan(site.getId(), today) == 0) continue;
                claimed = true;
                CrawlJob job = crawlService.createJob(rule.getId(), site.getOwnerId());
                jobCreated = true;
                job.setName(job.getName() + " · 每日增量");
                jobRepository.save(job);
                crawlWorker.discover(job.getId());
                log.info("Started scheduled crawl discovery job {} for site {}", job.getId(), site.getId());
            } catch (Exception exception) {
                if (claimed && !jobCreated) siteRepository.releaseDailyScan(site.getId(), today);
                log.warn("Could not start scheduled crawl for site {}: {}",
                        site.getId(), exception.getMessage());
            }
        }
    }
}
