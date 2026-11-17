package com.aiphoto.service;

import com.aiphoto.entity.CrawlAsset;
import com.aiphoto.entity.Photo;
import com.aiphoto.repository.CrawlAssetRepository;
import com.aiphoto.repository.PhotoRepository;
import com.aiphoto.storage.LocalStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlImportedCleanupService {
    private final CrawlAssetRepository assets;
    private final PhotoRepository photos;
    private final CrawlStagingStorageService staging;
    private final LocalStorageService storage;

    public static String thumbnailName(Photo photo) {
        boolean webp = photo.getOriginalFilename() != null
                && photo.getOriginalFilename().toLowerCase(java.util.Locale.ROOT).endsWith(".webp");
        return photo.getFileHashMd5() + "/thumb." + (webp ? "webp" : "jpg");
    }

    public enum CleanupResult { CLEANED, SKIPPED, FAILED }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CleanupResult cleanupOwned(Long id, Long ownerId) {
        return cleanupAsset(assets.lockOwnedCopy(id, ownerId).orElse(null), true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cleanup(Long id) {
        CrawlAsset asset = assets.lockCleanupAsset(id).orElse(null);
        cleanupAsset(asset, false);
    }

    private CleanupResult cleanupAsset(CrawlAsset asset, boolean manual) {
        if (asset == null || !manual && asset.getCleanupAt() == null || asset.getImportedPhotoId() == null
                || (asset.getStatus() != CrawlAsset.Status.IMPORTED && asset.getStatus() != CrawlAsset.Status.DUPLICATE)) return CleanupResult.SKIPPED;
        if (asset.getLocalPath() == null && asset.getThumbnailPath() == null) return CleanupResult.SKIPPED;
        Photo photo = photos.findById(asset.getImportedPhotoId()).orElse(null);
        if (photo == null || photo.getDeletedAt() != null) return CleanupResult.SKIPPED;
        if (manual) asset.setCleanupAt(java.time.LocalDateTime.now());
        try {
            // Confirm both library files are readable before removing the staging copy.
            try (var input = storage.downloadPhoto(photo.getFilePath())) {
                if (input.read() == -1) throw new IllegalStateException("图库原图为空");
            }
            if (storage.downloadThumbnailBytes(thumbnailName(photo)).length == 0) {
                throw new IllegalStateException("图库缩略图为空");
            }
            staging.delete(asset.getLocalPath());
            staging.delete(asset.getThumbnailPath());
            asset.setLocalPath(null);
            asset.setThumbnailPath(null);
            asset.setCleanupAt(null);
            assets.save(asset);
            return CleanupResult.CLEANED;
        } catch (Exception exception) {
            log.warn("Imported staging cleanup {} will retry: {}", asset.getId(), exception.getMessage());
            assets.save(asset);
            return CleanupResult.FAILED;
        }
    }
}
