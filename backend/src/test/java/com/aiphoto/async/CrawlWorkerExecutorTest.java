package com.aiphoto.async;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.aiphoto.entity.CrawlJob;
import com.aiphoto.entity.CrawlRule;
import com.aiphoto.repository.*;
import com.aiphoto.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CrawlWorkerExecutorTest {
    @Mock CrawlerExecutorService executors;
    @Mock CrawlerExecutorService.Session session;
    @Mock CrawlJobRepository jobs;
    @Mock CrawlRuleRepository rules;
    @Mock CrawlPageRepository pages;
    @Mock CrawlAssetRepository assets;
    @Mock SafeHttpFetcher fetcher;
    @Mock CrawlStagingStorageService storage;
    @Mock ObjectMapper mapper;
    @Mock CrawlJobQueueService queue;
    @Mock PerceptualHashService hashes;
    @Mock CrawlDiscoveryHistoryService history;
    @Mock CrawlImageSkipRepository skips;
    @Mock CrawlAssetSourceRepository sources;
    @InjectMocks CrawlWorker worker;

    @Test void controlFailurePausesDiscoveryAndReleasesExecutor() throws Exception {
        CrawlJob job = new CrawlJob(); job.setId(1L); job.setRuleSnapshot("snapshot");
        CrawlRule rule = new CrawlRule(); rule.setStartUrl("https://example.com/");
        when(executors.acquire(1L)).thenReturn(session);
        when(queue.newWorkerId()).thenReturn("worker");
        when(queue.claim(1L, "worker")).thenReturn(true);
        when(queue.renew(1L, "worker")).thenReturn(true);
        when(jobs.findById(1L)).thenReturn(Optional.of(job));
        when(mapper.readValue("snapshot", CrawlRule.class)).thenReturn(rule);
        when(fetcher.fetch(anyString(), any(), anyLong(), anyLong()))
                .thenThrow(new CrawlerExecutorService.ControlException("控制 API 不可用", null));
        worker.discover(1L);
        assertThat(job.getStatus()).isEqualTo(CrawlJob.Status.PAUSED);
        assertThat(job.getErrorMessage()).isEqualTo("控制 API 不可用");
        assertThat(job.getFinishedAt()).isNull();
        verify(session).close();
    }

    @Test void noAvailableExecutorLeavesJobQueued() {
        worker.discover(1L);
        verifyNoInteractions(queue, jobs, fetcher);
    }

    @Test void failedLeaseClaimReleasesExecutor() {
        when(executors.acquire(1L)).thenReturn(session);
        when(queue.newWorkerId()).thenReturn("worker");
        worker.download(1L);
        verify(session).close();
        verifyNoInteractions(jobs, fetcher);
    }
}
