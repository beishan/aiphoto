package com.aiphoto.service;

import com.aiphoto.async.CrawlWorker;
import com.aiphoto.async.CrawlImportWorker;
import com.aiphoto.entity.CrawlJob;
import com.aiphoto.repository.CrawlJobRepository;
import com.aiphoto.repository.CrawlImportBatchRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlRecoveryService {

    private final CrawlJobRepository jobRepository;
    private final CrawlWorker crawlWorker;
    private final CrawlImportBatchRepository importBatchRepository;
    private final CrawlImportWorker importWorker;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedJobs() {
        dispatchRunnableJobs();
    }

    @Scheduled(fixedDelayString = "${app.crawler.dispatch-interval-ms:10000}")
    public void dispatchRunnableJobs() {
        for (Long jobId : jobRepository.findRunnableJobIds(LocalDateTime.now())) {
            try {
                CrawlJob job = jobRepository.findById(jobId).orElse(null);
                if (job == null) continue;
                if (job.getPhase() == CrawlJob.Phase.DISCOVERY) {
                    crawlWorker.discover(jobId);
                } else if (job.getPhase() == CrawlJob.Phase.DOWNLOAD
                        || job.getPhase() == CrawlJob.Phase.REVIEW) {
                    crawlWorker.download(jobId);
                }
            } catch (Exception exception) {
                log.warn("Could not dispatch crawl job {}: {}", jobId, exception.getMessage());
            }
        }
        for (Long batchId : importBatchRepository.findRunnableBatchIds(LocalDateTime.now())) {
            importWorker.process(batchId);
        }
    }
}
