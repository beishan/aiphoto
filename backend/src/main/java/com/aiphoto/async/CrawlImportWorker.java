package com.aiphoto.async;

import com.aiphoto.service.CrawlImportBatchQueueService;
import com.aiphoto.service.CrawlImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlImportWorker {

    private final CrawlImportBatchQueueService queueService;
    private final CrawlImportService importService;

    @Async
    public void process(Long batchId) {
        String workerId = queueService.newWorkerId();
        if (!queueService.claim(batchId, workerId)) return;
        try {
            importService.processBatch(batchId, workerId);
        } catch (Exception exception) {
            log.warn("Import batch {} was interrupted: {}", batchId, exception.getMessage());
        }
    }
}
