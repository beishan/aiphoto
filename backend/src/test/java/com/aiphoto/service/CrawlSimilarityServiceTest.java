package com.aiphoto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiphoto.entity.CrawlAsset;
import com.aiphoto.repository.CrawlAssetRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CrawlSimilarityServiceTest {

    @Mock private CrawlService crawlService;
    @Mock private CrawlAssetRepository assetRepository;
    @Mock private CrawlStagingStorageService stagingStorage;

    @Test
    void persistsOnlyGroupsWithinTheBitDistanceThreshold() {
        CrawlAsset first = asset(1L, "0000000000000000");
        CrawlAsset second = asset(2L, "0000000000000001");
        CrawlAsset different = asset(3L, "ffffffffffffffff");
        when(assetRepository.findByJobIdAndStatus(8L, CrawlAsset.Status.DOWNLOADED))
                .thenReturn(List.of(first, second, different));
        CrawlSimilarityService service = new CrawlSimilarityService(
                crawlService, assetRepository, new PerceptualHashService(), stagingStorage);

        CrawlSimilarityService.SimilarityResult result = service.analyze(8L, 9L, 1);

        assertThat(result.analyzed()).isEqualTo(3);
        assertThat(result.groups()).isEqualTo(1);
        assertThat(result.matched()).isEqualTo(2);
        assertThat(first.getSimilarityGroupId()).isEqualTo(1L);
        assertThat(second.getSimilarityGroupId()).isEqualTo(1L);
        assertThat(first.getSimilarityCount()).isEqualTo(2);
        assertThat(different.getSimilarityGroupId()).isNull();
        verify(crawlService).getJob(8L, 9L);
        verify(assetRepository).clearSimilarityGroups(8L);
        verify(assetRepository).saveAll(List.of(first, second, different));
    }

    private CrawlAsset asset(Long id, String hash) {
        CrawlAsset asset = new CrawlAsset();
        asset.setId(id);
        asset.setFileHashPhash(hash);
        return asset;
    }
}
