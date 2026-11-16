package com.aiphoto.service;

import com.aiphoto.entity.CrawlJob;
import com.aiphoto.entity.CrawlerSetting;
import com.aiphoto.repository.CrawlJobRepository;
import com.aiphoto.repository.CrawlerSettingRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlAutoRetryScheduler {
    private final CrawlJobRepository jobs;
    private final CrawlerSettingRepository settings;
    private final CrawlService service;

    @Scheduled(fixedDelay = 10000)
    public void retryFailedJobs() {
        CrawlerSetting setting = settings.findById(1L).orElseGet(CrawlerSetting::new);
        if (!Boolean.TRUE.equals(setting.getTaskAutoRetryEnabled())) return;
        for (CrawlJob job : jobs.findByStatusIn(List.of(CrawlJob.Status.FAILED, CrawlJob.Status.PARTIAL))) {
            try {
                service.autoRetry(job.getId(), setting);
            } catch (Exception exception) {
                log.warn("Could not automatically retry crawl job {}: {}", job.getId(), exception.getMessage());
            }
        }
    }
}
