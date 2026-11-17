package com.aiphoto.service;

import com.aiphoto.repository.CrawlAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlImportedCopiesService {
    private final CrawlAssetRepository assets;
    private final CrawlImportedCleanupService cleanup;

    public record Summary(int cleaned, int skipped, int failed) {}

    public Summary cleanup(Long ownerId) {
        int cleaned = 0, skipped = 0, failed = 0;
        long cursor = 0;
        while (true) {
            var ids = assets.importedCopies(ownerId, cursor, PageRequest.of(0, 100));
            if (ids.isEmpty()) break;
            for (Long id : ids) {
                cursor = id;
                try {
                    switch (cleanup.cleanupOwned(id, ownerId)) {
                        case CLEANED -> cleaned++;
                        case SKIPPED -> skipped++;
                        case FAILED -> failed++;
                    }
                } catch (Exception exception) {
                    failed++;
                    log.warn("Could not clean imported copy {}: {}", id, exception.getMessage());
                }
            }
        }
        return new Summary(cleaned, skipped, failed);
    }
}
