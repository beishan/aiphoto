package com.aiphoto.service;

import com.aiphoto.entity.CrawlAsset;
import com.aiphoto.repository.CrawlAssetRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class CrawlDeletedCleanupServiceTest {
    @Test void deletesExpiredStagingFilesAndClearsPaths() throws Exception {
        var assets = mock(CrawlAssetRepository.class);
        var storage = mock(CrawlStagingStorageService.class);
        var service = new CrawlDeletedCleanupService(assets, storage, mock(SettingService.class));
        var asset = new CrawlAsset(); asset.setId(1L); asset.setStatus(CrawlAsset.Status.DELETED);
        asset.setLocalPath("1/original.gif"); asset.setThumbnailPath("1/thumb.jpg");
        asset.setCleanupAt(LocalDateTime.now().minusMinutes(1));
        when(assets.expiredDeleted(any(), any())).thenReturn(List.of(1L));
        when(assets.lockCleanupAsset(1L)).thenReturn(Optional.of(asset));
        service.cleanup();
        verify(storage).delete("1/original.gif"); verify(storage).delete("1/thumb.jpg");
        assertNull(asset.getLocalPath()); assertNull(asset.getCleanupAt());
        verify(storage, never()).delete(contains("skip-thumbnails"));
    }
    @Test void restoredAssetsAreNotDeletedAndRetentionIsUserScoped() {
        var assets = mock(CrawlAssetRepository.class); var storage = mock(CrawlStagingStorageService.class);
        var settings = mock(SettingService.class);
        when(settings.getSetting(7L, "crawl_deleted_retention_minutes")).thenReturn("120");
        var service = new CrawlDeletedCleanupService(assets, storage, settings);
        assertTrue(service.deadline(7L).isAfter(LocalDateTime.now().plusMinutes(119)));
        var asset = new CrawlAsset(); asset.setStatus(CrawlAsset.Status.DOWNLOADED);
        when(assets.expiredDeleted(any(), any())).thenReturn(List.of(1L));
        when(assets.lockCleanupAsset(1L)).thenReturn(Optional.of(asset));
        service.cleanup(); verifyNoInteractions(storage);
    }
}
