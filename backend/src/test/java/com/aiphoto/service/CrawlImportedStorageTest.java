package com.aiphoto.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.aiphoto.entity.*;
import com.aiphoto.repository.*;
import com.aiphoto.storage.LocalStorageService;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class CrawlImportedStorageTest {
    @Test
    void cleanupDeletesOnlyStagingAfterBothLibraryFilesAreReadable() throws Exception {
        var assets = mock(CrawlAssetRepository.class);
        var photos = mock(PhotoRepository.class);
        var staging = mock(CrawlStagingStorageService.class);
        var storage = mock(LocalStorageService.class);
        var service = new CrawlImportedCleanupService(assets, photos, staging, storage);
        CrawlAsset asset = imported();
        Photo photo = photo();
        when(assets.lockCleanupAsset(1L)).thenReturn(Optional.of(asset));
        when(photos.findById(2L)).thenReturn(Optional.of(photo));
        when(storage.downloadPhoto("hash/a.jpg")).thenReturn(new ByteArrayInputStream(new byte[]{1}));
        when(storage.downloadThumbnailBytes("hash/thumb.jpg")).thenReturn(new byte[]{2});
        service.cleanup(1L);
        verify(staging).delete("1/original.jpg");
        verify(staging).delete("1/thumbnail.jpg");
        assertThat(asset.getLocalPath()).isNull();
        assertThat(asset.getThumbnailPath()).isNull();
        assertThat(asset.getCleanupAt()).isNull();
        verify(storage, never()).deleteObject(anyString());
    }

    @Test
    void missingLibraryFilesKeepStagingAndRetryMarker() throws Exception {
        var assets = mock(CrawlAssetRepository.class);
        var photos = mock(PhotoRepository.class);
        var staging = mock(CrawlStagingStorageService.class);
        var storage = mock(LocalStorageService.class);
        CrawlAsset asset = imported();
        when(assets.lockCleanupAsset(1L)).thenReturn(Optional.of(asset));
        when(photos.findById(2L)).thenReturn(Optional.of(photo()));
        when(storage.downloadPhoto(anyString())).thenThrow(new java.io.IOException("missing"));
        new CrawlImportedCleanupService(assets, photos, staging, storage).cleanup(1L);
        verifyNoInteractions(staging);
        assertThat(asset.getLocalPath()).isEqualTo("1/original.jpg");
        assertThat(asset.getCleanupAt()).isNotNull();
    }

    @Test
    void importedPreviewReadsLibraryEvenWhenStagingPathsStillExist() throws Exception {
        var photos = mock(PhotoRepository.class);
        var storage = mock(LocalStorageService.class);
        var staging = mock(CrawlStagingStorageService.class);
        when(photos.findById(2L)).thenReturn(Optional.of(photo()));
        when(storage.downloadBytes("hash/a.jpg")).thenReturn(new byte[]{1});
        when(storage.downloadThumbnailBytes("hash/thumb.jpg")).thenReturn(new byte[]{2});
        var service = new CrawlAssetMediaService(photos, storage, staging);
        assertThat(service.read(imported(), false).bytes()).containsExactly((byte) 1);
        assertThat(service.read(imported(), true).bytes()).containsExactly((byte) 2);
        verifyNoInteractions(staging);
    }

    @Test
    void importSchedulesCleanupOnlyAfterCommit() throws Exception {
        var assets = mock(CrawlAssetRepository.class);
        var photos = mock(PhotoRepository.class);
        var cleanup = mock(CrawlImportedCleanupService.class);
        var service = new CrawlImportItemService(assets, photos, mock(PhotoSourceRepository.class),
                mock(CrawlStagingStorageService.class), mock(LocalStorageService.class),
                mock(AiTaskRepository.class), mock(com.aiphoto.async.PhotoIndexingService.class),
                mock(AlbumRepository.class), mock(TagRepository.class), mock(PhotoTagRepository.class));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "importedCleanup", cleanup);
        CrawlAsset asset = imported();
        when(assets.findByIdAndJobId(1L, 3L)).thenReturn(Optional.of(asset));
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.importOne(1L, 3L, 9L, null, java.util.List.of());
            verifyNoInteractions(cleanup);
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
            verify(cleanup).cleanup(1L);
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }


    @Test
    void manualCleanupIncludesHistoricalCopiesWithoutMarkerAndChecksOwner() throws Exception {
        var assets = mock(CrawlAssetRepository.class);
        var photos = mock(PhotoRepository.class);
        var staging = mock(CrawlStagingStorageService.class);
        var storage = mock(LocalStorageService.class);
        var asset = imported(); asset.setCleanupAt(null);
        when(assets.lockOwnedCopy(1L, 9L)).thenReturn(Optional.of(asset));
        when(photos.findById(2L)).thenReturn(Optional.of(photo()));
        when(storage.downloadPhoto("hash/a.jpg")).thenReturn(new ByteArrayInputStream(new byte[]{1}));
        when(storage.downloadThumbnailBytes("hash/thumb.jpg")).thenReturn(new byte[]{2});
        var service = new CrawlImportedCleanupService(assets, photos, staging, storage);
        assertThat(service.cleanupOwned(1L, 9L)).isEqualTo(CrawlImportedCleanupService.CleanupResult.CLEANED);
        assertThat(service.cleanupOwned(1L, 10L)).isEqualTo(CrawlImportedCleanupService.CleanupResult.SKIPPED);
        verify(staging, times(1)).delete("1/original.jpg");
    }

    @Test
    void batchCleanupAdvancesPastSkippedAndFailedItemsAndCountsResults() {
        var assets = mock(CrawlAssetRepository.class);
        var cleanup = mock(CrawlImportedCleanupService.class);
        when(assets.importedCopies(eq(9L), eq(0L), any())).thenReturn(java.util.List.of(1L, 2L, 3L));
        when(assets.importedCopies(eq(9L), eq(3L), any())).thenReturn(java.util.List.of());
        when(cleanup.cleanupOwned(1L, 9L)).thenReturn(CrawlImportedCleanupService.CleanupResult.CLEANED);
        when(cleanup.cleanupOwned(2L, 9L)).thenReturn(CrawlImportedCleanupService.CleanupResult.SKIPPED);
        when(cleanup.cleanupOwned(3L, 9L)).thenReturn(CrawlImportedCleanupService.CleanupResult.FAILED);
        var result = new CrawlImportedCopiesService(assets, cleanup).cleanup(9L);
        assertThat(result.cleaned()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        verify(assets).importedCopies(eq(9L), eq(3L), any());
    }

    private CrawlAsset imported() {
        CrawlAsset asset = new CrawlAsset();
        asset.setId(1L); asset.setImportedPhotoId(2L); asset.setStatus(CrawlAsset.Status.IMPORTED);
        asset.setLocalPath("1/original.jpg"); asset.setThumbnailPath("1/thumbnail.jpg");
        asset.setCleanupAt(java.time.LocalDateTime.now());
        return asset;
    }
    private Photo photo() {
        Photo photo = new Photo(); photo.setId(2L); photo.setFilePath("hash/a.jpg");
        photo.setFileHashMd5("hash"); photo.setOriginalFilename("a.jpg");
        return photo;
    }
}
