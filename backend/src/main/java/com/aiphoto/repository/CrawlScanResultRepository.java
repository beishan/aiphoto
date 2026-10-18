package com.aiphoto.repository;

import com.aiphoto.entity.CrawlScanResult;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlScanResultRepository extends JpaRepository<CrawlScanResult, Long> {
    Optional<CrawlScanResult> findByJobIdAndUrlHash(Long jobId, String urlHash);
    Page<CrawlScanResult> findByJobIdOrderByIdAsc(Long jobId, Pageable pageable);
    long countByJobIdAndResult(Long jobId, String result);
}
