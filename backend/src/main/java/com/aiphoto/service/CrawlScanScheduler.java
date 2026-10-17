package com.aiphoto.service;

import com.aiphoto.repository.CrawlScanPageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlScanScheduler {
    private final CrawlScanPageRepository repository;
    private final CrawlWorkflowService workflow;

    @Scheduled(fixedDelayString = "${app.crawler.schedule-check-interval-ms:60000}", initialDelay = 30000)
    public void dispatch() {
        for (var page : repository.findByAutoScanEnabledTrue()) {
            try {
                workflow.scan(page.getId(), page.getOwnerId(), true);
            } catch (Exception e) {
                log.warn("Could not schedule scan page {}: {}", page.getId(), e.getMessage());
            }
        }
    }
}
