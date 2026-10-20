package com.aiphoto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiphoto.entity.CrawlAsset;
import com.aiphoto.entity.CrawlJob;
import com.aiphoto.entity.CrawlRule;
import com.aiphoto.entity.CrawlSite;
import com.aiphoto.entity.Photo;
import com.aiphoto.repository.CrawlAssetRepository;
import com.aiphoto.repository.CrawlJobRepository;
import com.aiphoto.repository.CrawlPageRepository;
import com.aiphoto.repository.CrawlRuleRepository;
import com.aiphoto.repository.CrawlSiteRepository;
import com.aiphoto.repository.PhotoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class CrawlServiceTest {

    @Mock private CrawlRuleRepository ruleRepository;
    @Mock private CrawlSiteRepository siteRepository;
    @Mock private CrawlJobRepository jobRepository;
    @Mock private CrawlPageRepository pageRepository;
    @Mock private CrawlAssetRepository assetRepository;
    @Mock private SafeHttpFetcher fetcher;
    @Mock private CrawlStagingStorageService stagingStorage;
    @Mock private PhotoRepository photoRepository;
    @Mock private CrawlerSettingsService crawlerSettingsService;
    private CrawlService crawlService;

    @BeforeEach
    void setUp() {
        crawlService = new CrawlService(
                ruleRepository,
                siteRepository,
                jobRepository,
                pageRepository,
                assetRepository,
                fetcher,
                new ObjectMapper(),
                stagingStorage,
                photoRepository,
                crawlerSettingsService);
    }

    @Test void reviewAssetsDefaultToAllNonDeletedImagesAcrossOwnerTasks() {
        CrawlAsset first = new CrawlAsset(); first.setId(1L); first.setJobId(10L);
        CrawlAsset second = new CrawlAsset(); second.setId(2L); second.setJobId(11L);
        var pageable = PageRequest.of(0, 60);
        var assets = new PageImpl<>(List.of(first, second), pageable, 2);
        when(assetRepository.findReviewAssets(9L, null, "ALL", "ALL", false, false, pageable)).thenReturn(assets);

        var result = crawlService.listReviewAssets(9L, null, "", false, false, pageable);

        assertThat(result.getContent()).containsExactly(first, second);
        verify(assetRepository).findReviewAssets(9L, null, "ALL", "ALL", false, false, pageable);
    }

    @Test void runningAndQueuedJobsAppearBeforeHistoryWithoutChangingHistoryOrder() {
        CrawlJob newestCompleted = job(1L, CrawlJob.Status.COMPLETED);
        CrawlJob newestRunning = job(2L, CrawlJob.Status.RUNNING);
        CrawlJob newestQueued = job(3L, CrawlJob.Status.QUEUED);
        CrawlJob olderFailed = job(4L, CrawlJob.Status.FAILED);
        when(jobRepository.findByOwnerIdOrderByCreatedAtDesc(9L))
                .thenReturn(List.of(newestCompleted, newestRunning, newestQueued, olderFailed));

        assertThat(crawlService.listJobs(9L))
                .containsExactly(newestRunning, newestQueued, newestCompleted, olderFailed);
    }

    private CrawlJob job(Long id, CrawlJob.Status status) {
        CrawlJob job = new CrawlJob();
        job.setId(id);
        job.setStatus(status);
        return job;
    }

    @Test void queuedScansAppearBeforeQueuedDownloadsWhileRunningTasksStayFirst() {
        CrawlJob download = job(1L, CrawlJob.Status.QUEUED);
        download.setPhase(CrawlJob.Phase.DOWNLOAD);
        CrawlJob scan = job(2L, CrawlJob.Status.QUEUED);
        CrawlJob running = job(3L, CrawlJob.Status.RUNNING);
        running.setPriority(0);
        when(jobRepository.findByOwnerIdOrderByCreatedAtDesc(9L))
                .thenReturn(List.of(download, scan, running));
        assertThat(crawlService.listJobs(9L)).containsExactly(running, scan, download);
    }

    @Test void priorityUpdatesAreScopedToTheJobOwner() {
        CrawlJob job = job(1L, CrawlJob.Status.QUEUED);
        when(jobRepository.findByIdAndOwnerId(1L, 9L)).thenReturn(Optional.of(job));
        when(jobRepository.save(job)).thenReturn(job);
        assertThat(crawlService.updateJobPriority(1L, 9L, 25).getPriority()).isEqualTo(25);
        assertThatThrownBy(() -> crawlService.updateJobPriority(1L, 10L, 100))
                .hasMessageContaining("不存在");
        verify(jobRepository).save(job);
    }

    @Test void invalidPriorityDoesNotReachTheRepository() {
        assertThatThrownBy(() -> crawlService.updateJobPriority(1L, 9L, 101))
                .isInstanceOf(IllegalArgumentException.class);
        verify(jobRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test void batchPrioritiesValidateEveryOwnerBeforeChangingAnyJob() {
        CrawlJob first = job(1L, CrawlJob.Status.QUEUED);
        int original = first.getPriority();
        when(jobRepository.findByIdAndOwnerId(1L, 9L)).thenReturn(Optional.of(first));
        when(jobRepository.findByIdAndOwnerId(2L, 9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> crawlService.updateJobPriorities(List.of(1L, 2L), 9L, 25))
                .hasMessageContaining("不存在");
        assertThat(first.getPriority()).isEqualTo(original);
        verify(jobRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }

    @Test void batchPrioritiesRestorePhaseDefaultsAndDeduplicateIds() {
        CrawlJob scan = job(1L, CrawlJob.Status.QUEUED);
        scan.setPhase(CrawlJob.Phase.DISCOVERY);
        CrawlJob collect = job(2L, CrawlJob.Status.QUEUED);
        collect.setPhase(CrawlJob.Phase.DOWNLOAD);
        when(jobRepository.findByIdAndOwnerId(1L, 9L)).thenReturn(Optional.of(scan));
        when(jobRepository.findByIdAndOwnerId(2L, 9L)).thenReturn(Optional.of(collect));
        when(crawlerSettingsService.getScanTaskDefaultPriority()).thenReturn(100);
        when(crawlerSettingsService.getCollectionTaskDefaultPriority()).thenReturn(75);
        crawlService.updateJobPriorities(List.of(1L, 2L, 1L), 9L, null);
        assertThat(scan.getPriority()).isEqualTo(100);
        assertThat(collect.getPriority()).isEqualTo(75);
        assertThat(scan.getPriorityCustomized()).isFalse();
        assertThat(collect.getPriorityCustomized()).isFalse();
        verify(jobRepository).saveAll(List.of(scan, collect));
    }

    @Test void batchPrioritiesApplyExplicitValueToEverySelectedJob() {
        CrawlJob first = job(1L, CrawlJob.Status.QUEUED);
        CrawlJob second = job(2L, CrawlJob.Status.PAUSED);
        when(jobRepository.findByIdAndOwnerId(1L, 9L)).thenReturn(Optional.of(first));
        when(jobRepository.findByIdAndOwnerId(2L, 9L)).thenReturn(Optional.of(second));
        crawlService.updateJobPriorities(List.of(1L, 2L), 9L, 25);
        assertThat(first.getPriority()).isEqualTo(25);
        assertThat(second.getPriority()).isEqualTo(25);
        assertThat(first.getPriorityCustomized()).isTrue();
        assertThat(second.getPriorityCustomized()).isTrue();
        verify(jobRepository).saveAll(List.of(first, second));
    }

    @Test void batchPrioritiesRejectEmptySelectionAndInvalidPriority() {
        assertThatThrownBy(() -> crawlService.updateJobPriorities(List.of(), 9L, 25))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> crawlService.updateJobPriorities(List.of(1L), 9L, 101))
                .isInstanceOf(IllegalArgumentException.class);
        verify(jobRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }

    @Test void emptyCompletedDownloadCanBeRetriedForDiagnosis() {
        CrawlJob job = new CrawlJob(); job.setId(1L); job.setOwnerId(9L);
        job.setPhase(CrawlJob.Phase.REVIEW); job.setStatus(CrawlJob.Status.COMPLETED);
        var page = new com.aiphoto.entity.CrawlPage();
        page.setStatus(com.aiphoto.entity.CrawlPage.Status.SUCCEEDED);
        when(jobRepository.findByIdAndOwnerId(1L, 9L)).thenReturn(Optional.of(job));
        when(pageRepository.findByJobIdAndStatusAndIncludedTrueOrderById(
                1L, com.aiphoto.entity.CrawlPage.Status.SUCCEEDED)).thenReturn(List.of(page));
        crawlService.prepareResumeOrRetry(1L, 9L);
        assertThat(page.getStatus()).isEqualTo(com.aiphoto.entity.CrawlPage.Status.PENDING);
        assertThat(job.getPhase()).isEqualTo(CrawlJob.Phase.DOWNLOAD);
        assertThat(job.getStatus()).isEqualTo(CrawlJob.Status.QUEUED);
    }

    @Test void completedDownloadWithImageRecordsCannotBeRetried() {
        CrawlJob job = new CrawlJob(); job.setId(1L); job.setOwnerId(9L);
        job.setPhase(CrawlJob.Phase.REVIEW); job.setStatus(CrawlJob.Status.COMPLETED);
        when(jobRepository.findByIdAndOwnerId(1L, 9L)).thenReturn(Optional.of(job));
        when(assetRepository.countByJobId(1L)).thenReturn(1L);
        assertThatThrownBy(() -> crawlService.prepareResumeOrRetry(1L, 9L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void retryRevisitsSuccessfulPagesContainingFailedImages() throws Exception {
        CrawlJob job = new CrawlJob(); job.setId(1L); job.setOwnerId(9L);
        job.setPhase(CrawlJob.Phase.REVIEW); job.setStatus(CrawlJob.Status.PARTIAL);
        job.setRuleId(2L);
        CrawlRule rule = new CrawlRule();
        rule.setId(2L); rule.setOwnerId(9L); rule.setSiteId(3L);
        rule.setImageUrlIncludes(null);
        CrawlSite site = new CrawlSite();
        site.setId(3L); site.setOwnerId(9L); site.setStartUrl("https://example.com/");
        site.setBlockedHosts("");
        var page = new com.aiphoto.entity.CrawlPage();
        page.setStatus(com.aiphoto.entity.CrawlPage.Status.SUCCEEDED);
        when(jobRepository.findByIdAndOwnerId(1L, 9L)).thenReturn(Optional.of(job));
        when(ruleRepository.findByIdAndOwnerId(2L, 9L)).thenReturn(Optional.of(rule));
        when(siteRepository.findByIdAndOwnerId(3L, 9L)).thenReturn(Optional.of(site));
        when(pageRepository.pagesWithFailedAssets(1L)).thenReturn(List.of(page));
        when(jobRepository.save(job)).thenReturn(job);
        crawlService.prepareResumeOrRetry(1L, 9L);
        assertThat(page.getStatus()).isEqualTo(com.aiphoto.entity.CrawlPage.Status.PENDING);
        assertThat(job.getPhase()).isEqualTo(CrawlJob.Phase.DOWNLOAD);
        assertThat(job.getStatus()).isEqualTo(CrawlJob.Status.QUEUED);
        assertThat(job.getRuleSnapshot()).isNotBlank();
        assertThat(new ObjectMapper().readValue(job.getRuleSnapshot(), CrawlRule.class).getImageUrlIncludes())
                .isNull();
    }

    @Test
    void savesIndependentInspectionProxy() {
        CrawlSite site = inspectionSite();
        when(crawlerSettingsService.validateInspectionProxy("SELECTED", 7L)).thenReturn("SELECTED");
        when(siteRepository.findByIdAndOwnerId(2L, 9L)).thenReturn(Optional.of(site));
        when(siteRepository.save(site)).thenReturn(site);

        assertThat(crawlService.saveSite(site, 9L).getInspectionProxyId()).isEqualTo(7L);
        assertThat(site.getInspectionProxyMode()).isEqualTo("SELECTED");
    }

    @Test
    void previewUsesSameInspectionNetworkForListAndDetail() throws Exception {
        CrawlSite site = inspectionSite();
        var network = new CrawlerSettingsService.NetworkSnapshot(false, 5, 10, 60, 0, 0, 0, List.of());
        when(siteRepository.findByIdAndOwnerId(2L, 9L)).thenReturn(Optional.of(site));
        when(crawlerSettingsService.inspectionNetwork("SELECTED", 7L)).thenReturn(network);
        when(fetcher.fetchForInspection("https://example.com/", "blocked.example", 5242880L, 1000L, network))
                .thenReturn(html("https://example.com/", "<a href='/detail'>detail</a>"));
        when(fetcher.fetchForInspection("https://example.com/detail", "blocked.example", 5242880L, 1000L, network))
                .thenReturn(html("https://example.com/detail", "<img src='/original.jpg'>"));
        CrawlRule rule = new CrawlRule();
        rule.setSiteId(2L); rule.setName("test"); rule.setDetailSelector("a");

        var result = crawlService.preview(rule, 9L);

        assertThat(result.detailUrls()).containsExactly("https://example.com/detail");
        assertThat(result.imageUrls()).containsExactly("https://example.com/original.jpg");
        verify(fetcher, never()).fetch(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void previewUsesDraftProxyWithoutSavingSite() throws Exception {
        CrawlSite site = inspectionSite();
        var network = new CrawlerSettingsService.NetworkSnapshot(true, 5, 10, 60, 0, 0, 0, List.of());
        when(siteRepository.findByIdAndOwnerId(2L, 9L)).thenReturn(Optional.of(site));
        when(crawlerSettingsService.inspectionNetwork("DIRECT", null)).thenReturn(network);
        when(fetcher.fetchForInspection("https://example.com/", "blocked.example", 5242880L, 1000L, network))
                .thenReturn(html("https://example.com/", "<p>No links</p>"));
        CrawlRule rule = new CrawlRule();
        rule.setSiteId(2L); rule.setName("test"); rule.setDetailSelector("a");

        crawlService.preview(rule, 9L, "DIRECT", null);

        assertThat(site.getInspectionProxyMode()).isEqualTo("SELECTED");
        verify(siteRepository, never()).save(site);
    }

    @Test
    void inspectionReturnsMetadataUsingDraftProxy() throws Exception {
        CrawlSite site = inspectionSite();
        site.setId(null); site.setBlockedHosts("");
        var network = new CrawlerSettingsService.NetworkSnapshot(false, 5, 10, 60, 0, 0, 0, List.of());
        when(crawlerSettingsService.inspectionNetwork("SELECTED", 7L)).thenReturn(network);
        when(fetcher.fetchForInspection("https://example.com/", "", 5242880L, 1000L, network))
                .thenReturn(html("https://example.com/", "<title>Example</title><meta name='description' content='Gallery'>"));

        var info = crawlService.inspectSite(site, 9L);

        assertThat(info.title()).isEqualTo("Example");
        assertThat(info.description()).isEqualTo("Gallery");
        org.mockito.Mockito.verifyNoInteractions(siteRepository, jobRepository);
    }

    @Test
    void inspectionRejectsAnotherOwnersSiteBeforeAnyRequest() {
        CrawlSite site = inspectionSite();
        when(siteRepository.findByIdAndOwnerId(2L, 9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> crawlService.inspectSite(site, 9L))
                .isInstanceOf(IllegalArgumentException.class);
        org.mockito.Mockito.verifyNoInteractions(fetcher, crawlerSettingsService);
    }

    @Test
    void discoveryPagesAreNormalizedDeduplicatedAndSavedInOrder() {
        CrawlSite site = inspectionSite();
        site.setDiscoveryUrls(" https://EXAMPLE.com:443/a#first \nhttps://example.com/b\nhttps://example.com/a#other");
        when(crawlerSettingsService.validateInspectionProxy("SELECTED", 7L)).thenReturn("SELECTED");
        when(siteRepository.findByIdAndOwnerId(2L, 9L)).thenReturn(Optional.of(site));
        when(siteRepository.save(site)).thenReturn(site);

        assertThat(crawlService.saveSite(site, 9L).getDiscoveryUrls())
                .isEqualTo("https://example.com/a\nhttps://example.com/b");
    }

    @Test
    void blankDiscoveryPagesRemainEmptyAndFollowChangedSiteEntry() {
        CrawlSite site = inspectionSite();
        site.setDiscoveryUrls(" \n ");
        when(crawlerSettingsService.validateInspectionProxy("SELECTED", 7L)).thenReturn("SELECTED");
        when(siteRepository.findByIdAndOwnerId(2L, 9L)).thenReturn(Optional.of(site));
        when(siteRepository.save(site)).thenReturn(site);

        assertThat(crawlService.saveSite(site, 9L).getDiscoveryUrls()).isNull();
    }

    @Test void emptyBlacklistStaysEmptyAndAllowsExternalDiscoveryPages() {
        CrawlSite site = inspectionSite(); site.setBlockedHosts("");
        site.setDiscoveryUrls("https://other.example/list");
        when(crawlerSettingsService.validateInspectionProxy("SELECTED", 7L)).thenReturn("SELECTED");
        when(siteRepository.findByIdAndOwnerId(2L, 9L)).thenReturn(Optional.of(site));
        when(siteRepository.save(site)).thenReturn(site);
        var saved = crawlService.saveSite(site, 9L);
        assertThat(saved.getBlockedHosts()).isEmpty();
        assertThat(saved.getDiscoveryUrls()).isEqualTo("https://other.example/list");
    }

    @Test
    void discoveryPageValidationRejectsBlockedHostsAndInvalidUrls() {
        CrawlSite site = inspectionSite();
        when(crawlerSettingsService.validateInspectionProxy("SELECTED", 7L)).thenReturn("SELECTED");
        when(siteRepository.findByIdAndOwnerId(2L, 9L)).thenReturn(Optional.of(site));
        for (String invalid : List.of("https://other.com/list", "/relative", "ftp://example.com/list",
                "https://user:password@example.com/list", "https://example.com:8443/list")) {
            site.setBlockedHosts("other.com");
            site.setDiscoveryUrls(invalid);
            assertThatThrownBy(() -> crawlService.saveSite(site, 9L))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("发现页");
        }
        verify(siteRepository, never()).save(site);
    }

    @Test
    void discoveryPageCountIsBounded() {
        CrawlSite site = inspectionSite();
        site.setDiscoveryUrls(java.util.stream.IntStream.range(0, 21)
                .mapToObj(i -> "https://example.com/list/" + i).collect(java.util.stream.Collectors.joining("\n")));
        when(crawlerSettingsService.validateInspectionProxy("SELECTED", 7L)).thenReturn("SELECTED");
        when(siteRepository.findByIdAndOwnerId(2L, 9L)).thenReturn(Optional.of(site));
        assertThatThrownBy(() -> crawlService.saveSite(site, 9L)).hasMessageContaining("20");
    }

    @Test
    void rulePreviewUsesFirstDiscoveryPageInsteadOfSiteEntry() throws Exception {
        CrawlSite site = inspectionSite();
        site.setDiscoveryUrls("https://example.com/list/a\nhttps://example.com/list/b");
        var network = new CrawlerSettingsService.NetworkSnapshot(false, 5, 10, 60, 0, 0, 0, List.of());
        when(siteRepository.findByIdAndOwnerId(2L, 9L)).thenReturn(Optional.of(site));
        when(crawlerSettingsService.inspectionNetwork("SELECTED", 7L)).thenReturn(network);
        when(fetcher.fetchForInspection("https://example.com/list/a", "blocked.example", 5242880L, 1000L, network))
                .thenReturn(html("https://example.com/list/a", "<a href='/detail'>detail</a>"));
        when(fetcher.fetchForInspection("https://example.com/detail", "blocked.example", 5242880L, 1000L, network))
                .thenReturn(html("https://example.com/detail", "<img src='/image.jpg'>"));
        CrawlRule rule = new CrawlRule();
        rule.setSiteId(2L); rule.setName("test"); rule.setDetailSelector("a");

        var preview = crawlService.preview(rule, 9L);

        assertThat(preview.detailUrls()).containsExactly("https://example.com/detail");
        assertThat(preview.rule().getDiscoveryUrls()).containsExactly(
                "https://example.com/list/a", "https://example.com/list/b");
    }

    @Test
    void taskSnapshotKeepsDiscoveryPagesWhenWebsiteChanges() throws Exception {
        CrawlSite site = inspectionSite();
        site.setDiscoveryUrls("https://example.com/a\nhttps://example.com/b");
        CrawlRule rule = new CrawlRule();
        rule.setId(3L); rule.setSiteId(2L); rule.setEnabled(true);
        when(ruleRepository.findByIdAndOwnerId(3L, 9L)).thenReturn(Optional.of(rule));
        when(siteRepository.findByIdAndOwnerId(2L, 9L)).thenReturn(Optional.of(site));
        when(jobRepository.save(org.mockito.ArgumentMatchers.any(CrawlJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CrawlJob job = crawlService.createJob(3L, 9L);
        site.setDiscoveryUrls("https://example.com/c");
        CrawlRule snapshot = new ObjectMapper().readValue(job.getRuleSnapshot(), CrawlRule.class);

        assertThat(snapshot.getDiscoveryUrls()).containsExactly(
                "https://example.com/a", "https://example.com/b");
    }

    private CrawlSite inspectionSite() {
        CrawlSite site = new CrawlSite();
        site.setId(2L); site.setName("Example"); site.setStartUrl("https://example.com/");
        site.setBlockedHosts("blocked.example"); site.setInspectionProxyMode("SELECTED");
        site.setInspectionProxyId(7L);
        return site;
    }

    private SafeHttpFetcher.FetchedResource html(String url, String body) {
        return new SafeHttpFetcher.FetchedResource(java.net.URI.create(url), "text/html",
                body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void purgeDeletesJobOnlyAfterEveryStagingFileIsDeleted() throws Exception {
        CrawlJob job = completedJob();
        CrawlAsset asset = asset("8/1.jpg", "8/1.thumb.jpg");
        when(jobRepository.findByIdAndOwnerId(8L, 9L)).thenReturn(Optional.of(job));
        when(assetRepository.findByJobIdOrderByIdDesc(8L)).thenReturn(List.of(asset));

        CrawlService.PurgeResult result = crawlService.purgeJob(8L, 9L);

        assertThat(result.success()).isEqualTo(1);
        assertThat(result.fileFail()).isZero();
        verify(stagingStorage).delete("8/1.jpg");
        verify(stagingStorage).delete("8/1.thumb.jpg");
        verify(jobRepository).delete(job);
    }

    @Test
    void purgeKeepsJobWhenAStagingFileCannotBeDeleted() throws Exception {
        CrawlJob job = completedJob();
        CrawlAsset asset = asset("8/1.jpg", "8/1.thumb.jpg");
        when(jobRepository.findByIdAndOwnerId(8L, 9L)).thenReturn(Optional.of(job));
        when(assetRepository.findByJobIdOrderByIdDesc(8L)).thenReturn(List.of(asset));
        doThrow(new IllegalStateException("disk error")).when(stagingStorage).delete("8/1.jpg");

        CrawlService.PurgeResult result = crawlService.purgeJob(8L, 9L);

        assertThat(result.success()).isZero();
        assertThat(result.fileFail()).isEqualTo(1);
        verify(jobRepository, never()).delete(job);
    }

    @Test
    void pageSelectionOnlyChangesPagesOwnedByTheJob() {
        CrawlJob job = awaitingJob();
        com.aiphoto.entity.CrawlPage first = new com.aiphoto.entity.CrawlPage();
        first.setIncluded(true);
        com.aiphoto.entity.CrawlPage second = new com.aiphoto.entity.CrawlPage();
        second.setIncluded(false);
        when(jobRepository.findByIdAndOwnerId(8L, 9L)).thenReturn(Optional.of(job));
        when(pageRepository.findByIdInAndJobId(List.of(1L, 2L), 8L))
                .thenReturn(List.of(first, second));
        when(pageRepository.countByJobIdAndIncludedTrue(8L)).thenReturn(0L);

        CrawlService.PageSelectionResult result = crawlService.setPagesIncluded(
                8L, 9L, List.of(1L, 2L, 1L), false);

        assertThat(result.success()).isEqualTo(1);
        assertThat(result.included()).isZero();
        assertThat(first.getIncluded()).isFalse();
        assertThat(second.getIncluded()).isFalse();
    }

    @Test
    void downloadCannotStartWhenEveryDiscoveredPageIsExcluded() {
        CrawlJob job = awaitingJob();
        when(jobRepository.findByIdAndOwnerId(8L, 9L)).thenReturn(Optional.of(job));
        when(pageRepository.countByJobIdAndIncludedTrue(8L)).thenReturn(0L);

        assertThatThrownBy(() -> crawlService.prepareDownload(8L, 9L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能开始下载");
        verify(jobRepository, never()).save(job);
    }

    @Test
    void assetPageUsesWholeJobForDuplicateAndLibraryState() {
        CrawlJob job = completedJob();
        CrawlAsset asset = asset("8/1.jpg", "8/1.thumb.jpg");
        asset.setFileHashMd5("same-hash");
        Photo trashPhoto = new Photo();
        trashPhoto.setFileHashMd5("same-hash");
        trashPhoto.setDeletedAt(java.time.LocalDateTime.now());
        PageRequest pageable = PageRequest.of(0, 60);
        when(jobRepository.findByIdAndOwnerId(8L, 9L)).thenReturn(Optional.of(job));
        when(assetRepository.findByJobIdOrderByIdDesc(8L, pageable))
                .thenReturn(new PageImpl<>(List.of(asset), pageable, 1));
        when(assetRepository.countHashes(
                8L, CrawlAsset.Status.DELETED, List.of("same-hash")))
                .thenReturn(List.<Object[]>of(new Object[] {"same-hash", 3L}));
        when(photoRepository.findByFileHashMd5InIncludingTrash(List.of("same-hash")))
                .thenReturn(List.of(trashPhoto));

        CrawlAsset result = crawlService.listAssets(
                8L, 9L, null, false, false, null, pageable).getContent().get(0);

        assertThat(result.getExactDuplicateCount()).isEqualTo(3);
        assertThat(result.getLibraryDuplicate()).isFalse();
        assertThat(result.getLibraryTrashDuplicate()).isTrue();
    }

    @Test
    void activatingRuleDeactivatesPreviousRuleAndUsesSiteConfiguration() {
        CrawlSite site = new CrawlSite();
        site.setId(3L);
        site.setOwnerId(9L);
        site.setName("示例站点");
        site.setStartUrl("https://example.com/gallery");
        site.setBlockedHosts("blocked.example,ads.example.com");
        site.setMinRequestIntervalMillis(10_000L);
        CrawlRule input = new CrawlRule();
        input.setSiteId(3L);
        input.setName("高清图规则");
        input.setEnabled(true);
        input.setDetailSelector("a.detail");
        when(siteRepository.findByIdAndOwnerId(3L, 9L)).thenReturn(Optional.of(site));
        when(ruleRepository.save(org.mockito.ArgumentMatchers.any(CrawlRule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CrawlRule saved = crawlService.saveRule(input, 9L);

        verify(ruleRepository).deactivateAll(3L);
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getStartUrl()).isEqualTo("https://example.com/gallery");
        assertThat(saved.getBlockedHosts()).isEqualTo("blocked.example,ads.example.com");
        assertThat(saved.getMinRequestIntervalMillis()).isEqualTo(10_000L);
    }

    @Test
    void inactiveRuleCannotCreateJob() {
        CrawlRule rule = new CrawlRule();
        rule.setId(4L);
        rule.setOwnerId(9L);
        rule.setEnabled(false);
        when(ruleRepository.findByIdAndOwnerId(4L, 9L)).thenReturn(Optional.of(rule));

        assertThatThrownBy(() -> crawlService.createJob(4L, 9L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("生效");
        verify(jobRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private CrawlJob completedJob() {
        CrawlJob job = new CrawlJob();
        job.setId(8L);
        job.setOwnerId(9L);
        job.setStatus(CrawlJob.Status.COMPLETED);
        return job;
    }

    private CrawlJob awaitingJob() {
        CrawlJob job = completedJob();
        job.setPhase(CrawlJob.Phase.AWAITING_CONFIRMATION);
        return job;
    }

    private CrawlAsset asset(String localPath, String thumbnailPath) {
        CrawlAsset asset = new CrawlAsset();
        asset.setLocalPath(localPath);
        asset.setThumbnailPath(thumbnailPath);
        return asset;
    }
}
