package com.aiphoto.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aiphoto.entity.*;
import com.aiphoto.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class CrawlWorkflowServiceTest {
    @Mock CrawlScanPageRepository scans;
    @Mock CrawlPageRepository pages;
    @Mock CrawlJobRepository jobs;
    @Mock CrawlRuleRepository rules;
    @Mock CrawlService crawl;
    @Mock CrawlSeenPageRepository seenPages;
    private final ObjectMapper mapper = new ObjectMapper();
    private CrawlWorkflowService workflow;

    @BeforeEach void setup() {
        workflow = new CrawlWorkflowService(scans, pages, jobs, rules, crawl, mapper, seenPages);
    }

    private CrawlScanPage scanPage() {
        CrawlScanPage page = new CrawlScanPage();
        page.setId(7L); page.setOwnerId(9L); page.setSiteId(2L);
        page.setName("最新图片"); page.setUrl("https://example.com/latest");
        return page;
    }

    private CrawlRule activeRule() {
        CrawlRule rule = new CrawlRule(); rule.setId(3L); rule.setSiteId(2L);
        rule.setEnabled(true); rule.setDetailSelector("a.detail");
        return rule;
    }

    private CrawlJob newJob() throws Exception {
        CrawlJob job = new CrawlJob(); job.setId(20L); job.setSiteId(2L);
        job.setRuleSnapshot(mapper.writeValueAsString(activeRule()));
        return job;
    }

    @Test void scansOneIndependentEntryWithItsOwnPageLimit() throws Exception {
        CrawlScanPage page = scanPage(); page.setMaxPages(17);
        when(scans.lockOwned(7L, 9L)).thenReturn(Optional.of(page));
        when(rules.findBySiteIdAndOwnerIdAndEnabledTrue(2L, 9L)).thenReturn(Optional.of(activeRule()));
        when(crawl.createJob(3L, 9L)).thenReturn(newJob());
        when(jobs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CrawlJob job = workflow.scan(7L, 9L, false);
        CrawlRule snapshot = mapper.readValue(job.getRuleSnapshot(), CrawlRule.class);
        assertThat(snapshot.getDiscoveryUrls()).containsExactly(page.getUrl());
        assertThat(snapshot.getMaxListPages()).isEqualTo(17);
        assertThat(job.getPhase()).isEqualTo(CrawlJob.Phase.DISCOVERY);
        assertThat(job.getScanPageId()).isEqualTo(7L);
        assertThat(page.getLastScanAt()).isNotNull();
        verifyNoInteractions(pages);
    }

    @Test void scheduledScanRespectsDisabledAndNotYetDueEntries() {
        CrawlScanPage page = scanPage();
        when(scans.lockOwned(7L, 9L)).thenReturn(Optional.of(page));
        assertThat(workflow.scan(7L, 9L, true)).isNull();
        page.setAutoScanEnabled(true); page.setLastScanAt(LocalDateTime.now());
        assertThat(workflow.scan(7L, 9L, true)).isNull();
        verifyNoInteractions(crawl, jobs, rules);
    }

    @Test void dueScheduledScanCreatesANewDiscoveryJob() throws Exception {
        CrawlScanPage page = scanPage(); page.setAutoScanEnabled(true);
        page.setLastScanAt(LocalDateTime.now().minusMinutes(361));
        when(scans.lockOwned(7L, 9L)).thenReturn(Optional.of(page));
        when(rules.findBySiteIdAndOwnerIdAndEnabledTrue(2L, 9L)).thenReturn(Optional.of(activeRule()));
        when(crawl.createJob(3L, 9L)).thenReturn(newJob());
        when(jobs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(workflow.scan(7L, 9L, true).getScanPageId()).isEqualTo(7L);
        assertThat(page.getLastScanAt()).isAfter(LocalDateTime.now().minusMinutes(1));
    }

    @Test void pausedOrQueuedScanCannotBeSubmittedAgain() {
        when(scans.lockOwned(7L, 9L)).thenReturn(Optional.of(scanPage()));
        when(jobs.existsByScanPageIdAndStatusIn(eq(7L), any())).thenReturn(true);
        assertThatThrownBy(() -> workflow.scan(7L, 9L, false)).hasMessageContaining("已有");
        verifyNoInteractions(crawl, rules);
    }

    @Test void createsExactlyOneCollectionJobAndCopiesOnlyRequestedPage() throws Exception {
        CrawlPage page = new CrawlPage(); page.setId(11L); page.setJobId(10L);
        page.setUrl("https://example.com/photos/1"); page.setNormalizedUrl(page.getUrl()); page.setUrlHash("hash");
        page.setName("[原创]家庭旅行记录[34P]");
        CrawlJob source = new CrawlJob(); source.setId(10L); source.setSiteId(2L);
        when(pages.lockById(11L)).thenReturn(Optional.of(page));
        when(crawl.getJob(10L, 9L)).thenReturn(source);
        when(rules.findBySiteIdAndOwnerIdAndEnabledTrue(2L, 9L)).thenReturn(Optional.of(activeRule()));
        when(crawl.createJob(3L, 9L)).thenReturn(newJob());
        when(jobs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CrawlJob result = workflow.collect(11L, 9L);
        assertThat(result.getPhase()).isEqualTo(CrawlJob.Phase.DOWNLOAD);
        assertThat(result.getSourcePageId()).isEqualTo(11L);
        assertThat(result.getPagesFound()).isEqualTo(1);
        assertThat(result.getName()).isEqualTo("采集 · [原创]家庭旅行记录[34P]");
        assertThat(page.getCollectionJobId()).isEqualTo(20L);
        ArgumentCaptor<CrawlPage> saved = ArgumentCaptor.forClass(CrawlPage.class);
        verify(pages, times(2)).save(saved.capture());
        CrawlPage target = saved.getAllValues().getFirst();
        assertThat(target.getJobId()).isEqualTo(20L);
        assertThat(target.getUrl()).isEqualTo(page.getUrl());
        assertThat(target.getName()).isEqualTo(page.getName());
        assertThat(page.getJobId()).isEqualTo(10L);
        when(crawl.getJob(20L, 9L)).thenReturn(result);
        assertThat(workflow.collect(11L, 9L)).isSameAs(result);
        verify(crawl, times(1)).createJob(3L, 9L);
    }

    @Test void collectingAnotherOwnersPageIsRejectedBeforeCreation() {
        CrawlPage page = new CrawlPage(); page.setJobId(10L);
        when(pages.lockById(11L)).thenReturn(Optional.of(page));
        when(crawl.getJob(10L, 9L)).thenThrow(new IllegalArgumentException("采集任务不存在"));
        assertThatThrownBy(() -> workflow.collect(11L, 9L)).hasMessageContaining("不存在");
        verifyNoInteractions(jobs, rules);
    }

    @Test void legacyDownloadingPageCannotStartASecondTask() {
        CrawlPage page = new CrawlPage(); page.setJobId(10L);
        CrawlJob source = new CrawlJob(); source.setPhase(CrawlJob.Phase.DOWNLOAD);
        when(pages.lockById(11L)).thenReturn(Optional.of(page));
        when(crawl.getJob(10L, 9L)).thenReturn(source);
        assertThatThrownBy(() -> workflow.collect(11L, 9L)).hasMessageContaining("原采集任务");
        verifyNoInteractions(jobs, rules);
    }

    @Test void pageListExposesCollectionStatusInsteadOfTheScanStatus() {
        CrawlPage page = new CrawlPage(); page.setJobId(10L); page.setCollectionJobId(20L); page.setName("旅行照片");
        CrawlJob scan = new CrawlJob(); scan.setId(10L); scan.setSiteId(2L); scan.setStatus(CrawlJob.Status.COMPLETED);
        CrawlJob collection = new CrawlJob(); collection.setId(20L); collection.setStatus(CrawlJob.Status.RUNNING);
        var pageable = PageRequest.of(0, 50);
        when(pages.discovered(9L, null, "", false, pageable)).thenReturn(new PageImpl<>(List.of(page)));
        when(crawl.getJob(10L, 9L)).thenReturn(scan);
        when(crawl.getJob(20L, 9L)).thenReturn(collection);
        var result = workflow.pages(9L, null, "", false, pageable).getContent().getFirst();
        assertThat(result.status()).isEqualTo("RUNNING");
        assertThat(result.collectable()).isFalse();
        assertThat(result.collectionJobId()).isEqualTo(20L);
        assertThat(result.name()).isEqualTo("旅行照片");
    }

    @Test void scanUrlMustNotBeBlockedAndIntervalMustBeValid() {
        CrawlSite site = new CrawlSite(); site.setId(2L); site.setBlockedHosts("other.example");
        when(crawl.getSite(2L, 9L)).thenReturn(site);
        CrawlScanPage input = scanPage(); input.setUrl("https://other.example/latest");
        assertThatThrownBy(() -> workflow.saveScan(null, input, 9L)).hasMessageContaining("禁止访问列表");
        input.setUrl("https://example.com/latest"); input.setScanIntervalMinutes(1);
        assertThatThrownBy(() -> workflow.saveScan(null, input, 9L)).hasMessageContaining("周期");
        verify(scans, never()).save(any());
    }
}
