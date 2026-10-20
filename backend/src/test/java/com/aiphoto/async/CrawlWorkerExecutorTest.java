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

    @Test
    void failedDownloadsPersistTheirElapsedTime() throws Exception {
        var asset = new com.aiphoto.entity.CrawlAsset();
        asset.setId(3L);
        asset.setImageUrl("https://example.com/a.webp");
        var rule = new CrawlRule();
        rule.setStartUrl("https://example.com/");
        when(fetcher.fetchImage(anyString(), any(), anyLong(), anyLong())).thenAnswer(invocation -> {
            assertThat(asset.getDownloadStartedAtMillis()).isNotNull();
            assertThat(asset.getDownloadDurationMillis()).isNull();
            throw new java.net.http.HttpTimeoutException("图片超时");
        });

        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                worker, "downloadAsset", 1L, 9L, rule, asset, new CrawlImageHostGuard());

        assertThat(asset.getStatus()).isEqualTo(com.aiphoto.entity.CrawlAsset.Status.FAILED);
        assertThat(asset.getDownloadDurationMillis()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(asset.getDownloadElapsedMillis()).isEqualTo(asset.getDownloadDurationMillis());
        verify(assets, times(2)).save(asset);
    }

    @Test
    void repeatedHostTimeoutsFastFailOnlyThatHostAndRemainRetryable() throws Exception {
        CrawlJob job = new CrawlJob(); job.setId(1L); job.setOwnerId(9L); job.setRuleSnapshot("snapshot");
        CrawlRule rule = new CrawlRule(); rule.setStartUrl("https://example.com/");
        var records = new java.util.ArrayList<com.aiphoto.entity.CrawlAsset>();
        for (int i = 0; i < 6; i++) {
            var asset = new com.aiphoto.entity.CrawlAsset();
            asset.setId((long) i + 1);
            asset.setImageUrl((i < 5 ? "https://bad.example/" : "https://healthy.example/") + i + ".webp");
            records.add(asset);
        }
        when(executors.acquire(1L)).thenReturn(session);
        when(executors.current()).thenReturn(session);
        when(queue.newWorkerId()).thenReturn("worker");
        when(queue.claim(1L, "worker")).thenReturn(true);
        when(queue.renew(1L, "worker")).thenReturn(true);
        when(jobs.findById(1L)).thenReturn(Optional.of(job));
        when(mapper.readValue("snapshot", CrawlRule.class)).thenReturn(rule);
        when(assets.findByJobIdAndStatusOrderByIdDesc(1L, com.aiphoto.entity.CrawlAsset.Status.PENDING))
                .thenReturn(records);
        when(assets.countByJobIdAndStatus(eq(1L), any())).thenAnswer(invocation ->
                records.stream().filter(asset -> asset.getStatus() == invocation.getArgument(1)).count());
        when(fetcher.fetchImage(anyString(), any(), anyLong(), anyLong())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            if (url.contains("bad.example")) throw new java.net.http.HttpTimeoutException("图片超时");
            return new SafeHttpFetcher.FetchedResource(java.net.URI.create(url), "image/webp", new byte[]{0});
        });

        worker.download(1L);

        assertThat(job.getStatus()).isEqualTo(CrawlJob.Status.PARTIAL);
        assertThat(job.getFailCount()).isEqualTo(5);
        assertThat(records.subList(0, 5)).allSatisfy(asset ->
                assertThat(asset.getStatus()).isEqualTo(com.aiphoto.entity.CrawlAsset.Status.FAILED));
        assertThat(records.subList(3, 5)).allSatisfy(asset -> {
            assertThat(asset.getErrorMessage()).contains("连续 3").contains("重试");
            assertThat(asset.getDownloadStartedAtMillis()).isNull();
            assertThat(asset.getDownloadDurationMillis()).isNull();
        });
        assertThat(records.getLast().getStatus()).isEqualTo(com.aiphoto.entity.CrawlAsset.Status.DOWNLOADED);
        verify(fetcher, times(3)).fetchImage(contains("bad.example"), any(), anyLong(), anyLong());
        verify(fetcher).fetchImage(contains("healthy.example"), any(), anyLong(), anyLong());
        verify(session).close();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"false, 10", "true, 10", "false, 1"})
    void registersAllPagesBeforeDownloadingAndPreservesPendingOnResume(
            boolean interrupt, int maxImages) throws Exception {
        CrawlJob job = new CrawlJob(); job.setId(1L); job.setOwnerId(9L); job.setRuleSnapshot("snapshot");
        CrawlRule rule = new CrawlRule(); rule.setStartUrl("https://example.com/");
        rule.setMaxImages(maxImages);
        var first = new com.aiphoto.entity.CrawlPage();
        first.setId(11L); first.setUrl("https://example.com/first");
        var second = new com.aiphoto.entity.CrawlPage();
        second.setId(12L); second.setUrl("https://example.com/second");
        var sourcePages = java.util.List.of(first, second);
        var records = new java.util.ArrayList<com.aiphoto.entity.CrawlAsset>();
        var attempted = new java.util.ArrayList<String>();
        when(executors.acquire(1L)).thenReturn(session);
        when(executors.current()).thenReturn(session);
        when(queue.newWorkerId()).thenReturn("worker");
        when(queue.claim(1L, "worker")).thenReturn(true);
        when(queue.renew(1L, "worker")).thenReturn(true);
        when(jobs.findById(1L)).thenReturn(Optional.of(job));
        when(mapper.readValue("snapshot", CrawlRule.class)).thenReturn(rule);
        when(pages.findByJobIdAndStatusAndIncludedTrueOrderById(
                1L, com.aiphoto.entity.CrawlPage.Status.PENDING)).thenAnswer(invocation ->
                        sourcePages.stream().filter(page -> page.getStatus()
                                == com.aiphoto.entity.CrawlPage.Status.PENDING).toList());
        when(fetcher.fetch(anyString(), any(), anyLong(), anyLong())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            String html = url.endsWith("first")
                    ? "<img src='/a.webp'><img src='/b.webp'>"
                    : "<img src='/a.webp'><img src='/c.webp'>";
            return new SafeHttpFetcher.FetchedResource(java.net.URI.create(url), "text/html",
                    html.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        });
        when(assets.findByJobIdAndUrlHash(eq(1L), anyString())).thenAnswer(invocation ->
                records.stream().filter(asset -> asset.getUrlHash().equals(invocation.getArgument(1))).findFirst());
        when(assets.save(any())).thenAnswer(invocation -> {
            com.aiphoto.entity.CrawlAsset asset = invocation.getArgument(0);
            if (asset.getId() == null) { asset.setId((long) records.size() + 1); records.add(asset); }
            return asset;
        });
        when(assets.findByJobIdAndStatusOrderById(1L, com.aiphoto.entity.CrawlAsset.Status.RUNNING))
                .thenAnswer(invocation -> records.stream().filter(asset -> asset.getStatus()
                        == com.aiphoto.entity.CrawlAsset.Status.RUNNING).toList());
        when(assets.findByJobIdAndStatusOrderByIdDesc(1L, com.aiphoto.entity.CrawlAsset.Status.PENDING))
                .thenAnswer(invocation -> records.stream().filter(asset -> asset.getStatus()
                        == com.aiphoto.entity.CrawlAsset.Status.PENDING).toList());
        when(assets.countByJobIdAndStatus(eq(1L), any())).thenAnswer(invocation ->
                records.stream().filter(asset -> asset.getStatus() == invocation.getArgument(1)).count());
        when(fetcher.fetchImage(anyString(), any(), anyLong(), anyLong())).thenAnswer(invocation -> {
            assertThat(records).hasSize(3);
            assertThat(sourcePages).allMatch(page -> page.getStatus() == com.aiphoto.entity.CrawlPage.Status.SUCCEEDED);
            String url = invocation.getArgument(0);
            attempted.add(url);
            if (interrupt && attempted.size() == 1) {
                throw new CrawlerExecutorService.ControlException("暂停下载", null);
            }
            return new SafeHttpFetcher.FetchedResource(java.net.URI.create(url), "image/webp", new byte[]{0});
        });

        worker.download(1L);
        if (interrupt) {
            assertThat(job.getStatus()).isEqualTo(CrawlJob.Status.PAUSED);
            assertThat(records.getFirst().getDownloadDurationMillis()).isNotNull();
            assertThat(records).allMatch(asset -> asset.getStatus() == com.aiphoto.entity.CrawlAsset.Status.PENDING);
            worker.download(1L);
        }

        assertThat(job.getStatus()).isEqualTo(CrawlJob.Status.COMPLETED);
        assertThat(records.stream().filter(asset -> asset.getStatus()
                == com.aiphoto.entity.CrawlAsset.Status.DOWNLOADED)).hasSize(Math.min(maxImages, 3));
        assertThat(records.stream().filter(asset -> asset.getStatus()
                == com.aiphoto.entity.CrawlAsset.Status.DOWNLOADED))
                .allSatisfy(asset -> {
                    assertThat(asset.getDownloadStartedAtMillis()).isNotNull();
                    assertThat(asset.getDownloadDurationMillis()).isNotNull().isGreaterThanOrEqualTo(0L);
                });
        assertThat(records).noneMatch(asset -> asset.getStatus() == com.aiphoto.entity.CrawlAsset.Status.PENDING);
        if (maxImages == 1) {
            assertThat(records.subList(1, 3)).allMatch(asset ->
                    asset.getStatus() == com.aiphoto.entity.CrawlAsset.Status.SKIPPED
                            && asset.getErrorMessage().contains("数量上限"));
        }
        assertThat(attempted).containsExactlyElementsOf(interrupt
                ? java.util.List.of("https://example.com/a.webp", "https://example.com/a.webp",
                        "https://example.com/b.webp", "https://example.com/c.webp")
                : maxImages == 1 ? java.util.List.of("https://example.com/a.webp")
                        : java.util.List.of("https://example.com/a.webp", "https://example.com/b.webp", "https://example.com/c.webp"));
        verify(fetcher, times(2)).fetch(anyString(), any(), anyLong(), anyLong());
        verify(sources, times(4)).save(any());
    }

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
