package com.aiphoto.repository;

import com.aiphoto.entity.CrawlImportItem;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlImportItemRepository extends JpaRepository<CrawlImportItem, Long> {
    Page<CrawlImportItem> findByBatchIdOrderById(Long batchId, Pageable pageable);
    List<CrawlImportItem> findByBatchIdOrderById(Long batchId);
    Page<CrawlImportItem> findByBatchIdAndStatusOrderById(
            Long batchId, CrawlImportItem.Status status, Pageable pageable);
    long countByBatchIdAndStatus(Long batchId, CrawlImportItem.Status status);
    boolean existsByBatchIdAndAssetId(Long batchId, Long assetId);
}
