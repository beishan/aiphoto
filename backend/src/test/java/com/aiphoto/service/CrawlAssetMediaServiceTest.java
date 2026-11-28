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
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
        asset.setContentType("image/png");
        asset.setLocalPath("20/1.png");

        assertThatThrownBy(() -> media.read(asset, true, true))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(staging);

        when(staging.read("20/1.png")).thenReturn(new byte[]{1, 2, 3});
        assertThat(media.read(asset, false).bytes()).containsExactly(1, 2, 3);
        assertThat(media.read(asset, true).bytes()).containsExactly(1, 2, 3);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "UklGRigAAABXRUJQVlA4TBwAAAAvH8MxEAdQssrUsngBAEX6/58i+p/63//+97+I",
        "UklGRoQAAABXRUJQVlA4WAoAAAACAAAADwAABwAAQU5JTQYAAAAAAAAAAABBTk1GKAAAAAAAAAAAAA8AAAcAAGQAAAJWUDhMDwAAAC8PwAEABxD9j/4HIqL/AQBBTk1GKAAAAAAAAAAAAA8AAAcAAGQAAABWUDhMDwAAAC8PwAEABxDR//4HIqL/AQA="
    })
    void legacyWebpReturnsSmallStaticJpeg(String fixture) throws Exception {
        var asset = new CrawlAsset();
        asset.setStatus(CrawlAsset.Status.DOWNLOADED);
        asset.setContentType("image/webp");
        asset.setLocalPath("20/1.webp");
        byte[] webp = Base64.getDecoder().decode(fixture);
        when(staging.read("20/1.webp")).thenReturn(webp);

        var result = media.read(asset, true, true);
        assertThat(result.type()).isEqualTo(MediaType.IMAGE_JPEG);
        var image = ImageIO.read(new ByteArrayInputStream(result.bytes()));
        assertThat(image).isNotNull();
        assertThat(Math.max(image.getWidth(), image.getHeight())).isLessThanOrEqualTo(400);
        assertThat(media.read(asset, false).bytes()).isEqualTo(webp);
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
