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
                photoRepository);
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
                8L, 9L, null, false, false, pageable).getContent().get(0);

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
        site.setAllowedHosts("example.com,cdn.example.com");
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
        assertThat(saved.getAllowedHosts()).isEqualTo("example.com,cdn.example.com");
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
