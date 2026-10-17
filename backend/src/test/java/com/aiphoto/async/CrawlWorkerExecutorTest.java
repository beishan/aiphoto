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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "<p>没有图片</p>, 图片选择器未匹配到元素",
            "<img title='photo'>, 没有有效图片地址",
            "<img src='/blocked.jpg'>, 全部被包含 / 排除规则过滤"
    })
    void emptyExtractionReportsCauseInsteadOfSuccess(String html, String reason) throws Exception {
        CrawlJob job = new CrawlJob(); job.setId(1L); job.setRuleSnapshot("snapshot");
        CrawlRule rule = new CrawlRule(); rule.setStartUrl("https://example.com/");
        rule.setImageUrlExcludes("blocked");
        com.aiphoto.entity.CrawlPage page = new com.aiphoto.entity.CrawlPage();
        page.setId(2L); page.setUrl("https://example.com/detail");
        when(executors.acquire(1L)).thenReturn(session);
        when(queue.newWorkerId()).thenReturn("worker");
        when(queue.claim(1L, "worker")).thenReturn(true);
        when(queue.renew(1L, "worker")).thenReturn(true);
        when(jobs.findById(1L)).thenReturn(Optional.of(job));
        when(mapper.readValue("snapshot", CrawlRule.class)).thenReturn(rule);
        when(pages.findByJobIdAndStatusAndIncludedTrueOrderById(
                1L, com.aiphoto.entity.CrawlPage.Status.PENDING)).thenReturn(java.util.List.of(page));
        when(pages.countByJobIdAndStatusIn(eq(1L), anyList())).thenReturn(1L);
        when(fetcher.fetch(anyString(), any(), anyLong(), anyLong())).thenReturn(
                new SafeHttpFetcher.FetchedResource(java.net.URI.create(page.getUrl()),
                        "text/html", html.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        worker.download(1L);

        assertThat(page.getStatus()).isEqualTo(com.aiphoto.entity.CrawlPage.Status.FAILED);
        assertThat(page.getErrorMessage()).contains(reason).contains("通过过滤 0");
        assertThat(job.getStatus()).isEqualTo(CrawlJob.Status.PARTIAL);
        assertThat(job.getFailCount()).isEqualTo(1);
        verify(fetcher, never()).fetchImage(anyString(), any(), anyLong(), anyLong());
        verify(session, never()).taskCompleted(anyString());
        verify(session).close();
    }

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

    @Test void independentScanCompletesWithoutEnteringDownloadConfirmation() throws Exception {
        CrawlJob job = new CrawlJob(); job.setId(1L); job.setScanPageId(7L); job.setRuleSnapshot("snapshot");
        CrawlRule rule = new CrawlRule(); rule.setStartUrl("https://example.com/"); rule.setDetailSelector("a");
        when(executors.acquire(1L)).thenReturn(session);
        when(queue.newWorkerId()).thenReturn("worker");
        when(queue.claim(1L, "worker")).thenReturn(true);
        when(queue.renew(1L, "worker")).thenReturn(true);
        when(jobs.findById(1L)).thenReturn(Optional.of(job));
        when(mapper.readValue("snapshot", CrawlRule.class)).thenReturn(rule);
        when(fetcher.fetch(anyString(), any(), anyLong(), anyLong())).thenReturn(
                new SafeHttpFetcher.FetchedResource(java.net.URI.create("https://example.com/"),
                        "text/html", "<p>ok</p>".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        worker.discover(1L);
        assertThat(job.getPhase()).isEqualTo(CrawlJob.Phase.DISCOVERY);
        assertThat(job.getStatus()).isEqualTo(CrawlJob.Status.COMPLETED);
        assertThat(job.getFinishedAt()).isNotNull();
        verifyNoInteractions(assets, storage);
    }

    @Test void failedLeaseClaimReleasesExecutor() {
        when(executors.acquire(1L)).thenReturn(session);
        when(queue.newWorkerId()).thenReturn("worker");
        worker.download(1L);
        verify(session).close();
        verifyNoInteractions(jobs, fetcher);
    }
    @Test
    void discoveryVisitsAllConfiguredEntriesAndDeduplicatesPagination() throws Exception {
        CrawlJob job = new CrawlJob(); job.setId(1L); job.setRuleSnapshot("snapshot");
        CrawlRule rule = new CrawlRule();
        rule.setStartUrl("https://example.com/");
        rule.setBlockedHosts("example.com"); rule.setDetailSelector("a.detail"); rule.setNextSelector("a.next");
        rule.setSiteId(2L);
        rule.setDiscoveryUrls(java.util.List.of("https://example.com/a", "https://example.com/b"));
        when(executors.acquire(1L)).thenReturn(session);
        when(queue.newWorkerId()).thenReturn("worker");
        when(queue.claim(1L, "worker")).thenReturn(true);
        when(queue.renew(1L, "worker")).thenReturn(true);
        when(jobs.findById(1L)).thenReturn(Optional.of(job));
        when(mapper.readValue("snapshot", CrawlRule.class)).thenReturn(rule);
        when(fetcher.fetch(anyString(), any(), anyLong(), anyLong())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            String body = "<table><tr><td class='tal'><h3><a class='detail' href='/detail'>"
                    + "[原创]家庭旅行记录[34P]</a></h3><span class='thread_page'>"
                    + "[<a href='/read?page=1'>1</a><a href='/read?page=2'>2</a>]"
                    + "</span></td></tr></table><a class='next' href='/b'>Next</a>";
            return new SafeHttpFetcher.FetchedResource(java.net.URI.create(url), "text/html",
                    body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        });

        worker.discover(1L);

        verify(fetcher).fetch("https://example.com/a", "example.com", 5242880L, 1000L);
        verify(fetcher).fetch("https://example.com/b", "example.com", 5242880L, 1000L);
        verify(fetcher, times(2)).fetch(anyString(), any(), anyLong(), anyLong());
        verify(history, times(2)).addIfNew(eq(1L), eq(2L), eq("https://example.com/detail"),
                eq("https://example.com/detail"), anyString(), eq("[原创]家庭旅行记录[34P]"));
        assertThat(job.getStatus()).isEqualTo(CrawlJob.Status.COMPLETED);
        verify(session).close();
    }

    @Test
    void oneFailedEntryDoesNotPreventOtherEntriesFromDiscovery() throws Exception {
        CrawlJob job = new CrawlJob(); job.setId(1L); job.setRuleSnapshot("snapshot");
        CrawlRule rule = new CrawlRule(); rule.setStartUrl("https://example.com/");
        rule.setBlockedHosts("example.com"); rule.setDetailSelector("a");
        rule.setDiscoveryUrls(java.util.List.of("https://example.com/a", "https://example.com/b"));
        when(executors.acquire(1L)).thenReturn(session);
        when(queue.newWorkerId()).thenReturn("worker");
        when(queue.claim(1L, "worker")).thenReturn(true);
        when(queue.renew(1L, "worker")).thenReturn(true);
        when(jobs.findById(1L)).thenReturn(Optional.of(job));
        when(mapper.readValue("snapshot", CrawlRule.class)).thenReturn(rule);
        when(fetcher.fetch("https://example.com/a", "example.com", 5242880L, 1000L))
                .thenThrow(new java.io.IOException("unavailable"));
        when(fetcher.fetch("https://example.com/b", "example.com", 5242880L, 1000L))
                .thenReturn(new SafeHttpFetcher.FetchedResource(java.net.URI.create("https://example.com/b"),
                        "text/html", "<p>OK</p>".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        worker.discover(1L);

        verify(fetcher).fetch("https://example.com/b", "example.com", 5242880L, 1000L);
        assertThat(job.getStatus()).isEqualTo(CrawlJob.Status.PARTIAL);
        assertThat(job.getFailCount()).isEqualTo(1);
    }
}
