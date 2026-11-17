package com.aiphoto.service;

import com.aiphoto.entity.CrawlJob;
import com.aiphoto.entity.CrawlRetry;
import com.aiphoto.repository.CrawlRetryRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CrawlRetryHistoryService {
    private final CrawlRetryRepository repository;

    @Transactional
    public void queued(CrawlJob job, String type) {
        finish(job);
        CrawlRetry retry = new CrawlRetry();
        retry.setJobId(job.getId());
        retry.setRetryType(type);
        retry.setQueuedAt(LocalDateTime.now());
        repository.save(retry);
    }

    @Transactional
    public void started(Long jobId) {
        repository.findFirstByJobIdAndFinishedAtIsNullOrderByIdDesc(jobId).ifPresent(retry -> {
            if (retry.getStartedAt() == null) retry.setStartedAt(LocalDateTime.now());
            retry.setResult("RUNNING");
            repository.save(retry);
        });
    }

    @Transactional
    public void finish(CrawlJob job) {
        if (job.getStatus() == CrawlJob.Status.RUNNING || job.getStatus() == CrawlJob.Status.QUEUED) return;
        repository.findFirstByJobIdAndFinishedAtIsNullOrderByIdDesc(job.getId()).ifPresent(retry -> {
            retry.setFinishedAt(LocalDateTime.now());
            retry.setResult(job.getStatus().name());
            retry.setMessage(job.getErrorMessage());
            repository.save(retry);
        });
    }
}
