package com.aiphoto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiphoto.entity.CrawlAsset;
import com.aiphoto.entity.Photo;
import com.aiphoto.repository.PhotoRepository;
import com.aiphoto.storage.LocalStorageService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

class CrawlAssetMediaServiceTest {
    private final PhotoRepository photos = mock(PhotoRepository.class);
    private final LocalStorageService storage = mock(LocalStorageService.class);
    private final CrawlStagingStorageService staging = mock(CrawlStagingStorageService.class);
    private final CrawlAssetMediaService media = new CrawlAssetMediaService(photos, storage, staging);

    @Test
    void boundedThumbnailNeverReadsAnOriginalWhenThumbnailIsMissing() throws Exception {
        var asset = new CrawlAsset();
        asset.setStatus(CrawlAsset.Status.DOWNLOADED);
        asset.setContentType("image/webp");
        asset.setLocalPath("20/1.webp");

        assertThatThrownBy(() -> media.read(asset, true, true))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(staging);

        when(staging.read("20/1.webp")).thenReturn(new byte[]{1, 2, 3});
        assertThat(media.read(asset, false).bytes()).containsExactly(1, 2, 3);
        assertThat(media.read(asset, true).bytes()).containsExactly(1, 2, 3);
    }

    @Test
    void boundedThumbnailUsesStagedJpegInsteadOfAnImportedWebpCopy() throws Exception {
        var asset = new CrawlAsset();
        asset.setStatus(CrawlAsset.Status.IMPORTED);
        asset.setImportedPhotoId(14L);
        asset.setThumbnailPath("20/1.thumb.jpg");
        var photo = new Photo();
        photo.setOriginalFilename("original.webp");
        when(photos.findById(14L)).thenReturn(Optional.of(photo));
        when(staging.read("20/1.thumb.jpg")).thenReturn(new byte[]{4, 5, 6});

        var thumbnail = media.read(asset, true, true);

        assertThat(thumbnail.type()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(thumbnail.bytes()).containsExactly(4, 5, 6);
        verifyNoInteractions(storage);
    }
}
