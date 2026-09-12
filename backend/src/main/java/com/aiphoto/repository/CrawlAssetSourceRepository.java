package com.aiphoto.repository;

import com.aiphoto.entity.CrawlAssetSource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlAssetSourceRepository extends JpaRepository<CrawlAssetSource, Long> {
    boolean existsByAssetIdAndPageId(Long assetId, Long pageId);
}
