package com.aiphoto.service;

import com.aiphoto.entity.CrawlAsset;
import com.aiphoto.repository.CrawlAssetRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.PageRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlDeletedCleanupService {
    private final CrawlAssetRepository assets;
    private final CrawlStagingStorageService storage;
    private final SettingService settings;

    public LocalDateTime deadline(Long ownerId) {
        int minutes = 60;
        try {
            String value = settings.getSetting(ownerId, "crawl_deleted_retention_minutes");
            if (value != null) minutes = Math.clamp(Integer.parseInt(value), 1, 43200);
        } catch (NumberFormatException ignored) {}
        return LocalDateTime.now().plusMinutes(minutes);
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    @Transactional
    public void cleanup() {
        var now = LocalDateTime.now();
        for (var candidate : assets.expiredDeleted(now, PageRequest.of(0, 100))) {
            var asset = assets.lockCleanupAsset(candidate).orElse(null);
            if (asset == null || asset.getStatus() != CrawlAsset.Status.DELETED || asset.getCleanupAt() == null
                    || asset.getCleanupAt().isAfter(now) || asset.getLocalPath() == null) continue;
            try {
                storage.delete(asset.getLocalPath());
                storage.delete(asset.getThumbnailPath());
                asset.setLocalPath(null);
                asset.setThumbnailPath(null);
                asset.setCleanupAt(null);
                assets.save(asset);
            } catch (Exception e) { log.warn("Staging cleanup {} failed: {}", asset.getId(), e.getMessage()); }
        }
    }
}
