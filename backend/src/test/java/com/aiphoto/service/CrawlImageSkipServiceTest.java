package com.aiphoto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiphoto.entity.CrawlAsset;
import com.aiphoto.entity.CrawlImageSkip;
import com.aiphoto.repository.CrawlAssetRepository;
import com.aiphoto.repository.CrawlImageSkipRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CrawlImageSkipServiceTest {

    @Mock private CrawlService crawlService;
    @Mock private CrawlAssetRepository assetRepository;
    @Mock private CrawlImageSkipRepository skipRepository;

    @Test
    void createsPersistentSkipFromOwnedAsset() {
        CrawlAsset asset = new CrawlAsset();
        asset.setImageUrl("https://img.example.com/1.jpg");
        asset.setNormalizedUrl("https://img.example.com/1.jpg");
        asset.setUrlHash("hash");
        asset.setSourcePageUrl("https://example.com/post/1");
        when(assetRepository.findByIdInAndJobId(List.of(3L), 7L)).thenReturn(List.of(asset));
        when(skipRepository.findByOwnerIdAndUrlHash(9L, "hash")).thenReturn(Optional.empty());
        var service = new CrawlImageSkipService(crawlService, assetRepository, skipRepository);

        int count = service.addFromAssets(7L, List.of(3L), 9L, "重复图片");

        assertThat(count).isEqualTo(1);
        verify(crawlService).getJob(7L, 9L);
        ArgumentCaptor<CrawlImageSkip> skip = ArgumentCaptor.forClass(CrawlImageSkip.class);
        verify(skipRepository).save(skip.capture());
        assertThat(skip.getValue().getReason()).isEqualTo("重复图片");
        assertThat(skip.getValue().getSourcePageUrl()).isEqualTo("https://example.com/post/1");
    }
}
