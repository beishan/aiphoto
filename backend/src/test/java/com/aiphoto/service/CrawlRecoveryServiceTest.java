package com.aiphoto.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiphoto.async.CrawlWorker;
import com.aiphoto.async.CrawlImportWorker;
import com.aiphoto.entity.CrawlJob;
import com.aiphoto.repository.CrawlJobRepository;
import com.aiphoto.repository.CrawlImportBatchRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CrawlRecoveryServiceTest {

    @Mock private CrawlJobRepository jobRepository;
    @Mock private CrawlWorker crawlWorker;
    @Mock private CrawlImportBatchRepository importBatchRepository;
    @Mock private CrawlImportWorker importWorker;

    @Test
    void dispatchesRunnableJobsAccordingToTheirPersistedPhase() {
        CrawlJob discovery = job(1L, CrawlJob.Phase.DISCOVERY);
        CrawlJob download = job(2L, CrawlJob.Phase.DOWNLOAD);
        CrawlJob awaitingConfirmation = job(3L, CrawlJob.Phase.AWAITING_CONFIRMATION);
        when(jobRepository.findRunnableJobIds(ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(List.of(1L, 2L, 3L));
        when(jobRepository.findById(1L)).thenReturn(Optional.of(discovery));
        when(jobRepository.findById(2L)).thenReturn(Optional.of(download));
        when(jobRepository.findById(3L)).thenReturn(Optional.of(awaitingConfirmation));
        when(importBatchRepository.findRunnableBatchIds(
                ArgumentMatchers.any(LocalDateTime.class))).thenReturn(List.of(11L));

        new CrawlRecoveryService(
                jobRepository, crawlWorker, importBatchRepository, importWorker)
                .dispatchRunnableJobs();

        verify(crawlWorker).discover(1L);
        verify(crawlWorker).download(2L);
        verify(crawlWorker, never()).discover(3L);
        verify(crawlWorker, never()).download(3L);
        verify(importWorker).process(11L);
    }

    private CrawlJob job(Long id, CrawlJob.Phase phase) {
        CrawlJob job = new CrawlJob();
        job.setId(id);
        job.setPhase(phase);
        return job;
    }
}
