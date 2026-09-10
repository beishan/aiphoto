package com.aiphoto.repository;

import com.aiphoto.entity.CrawlPage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlPageRepository extends JpaRepository<CrawlPage, Long> {
    List<CrawlPage> findByJobIdOrderById(Long jobId);
    List<CrawlPage> findByJobIdAndStatusOrderById(Long jobId, CrawlPage.Status status);
    List<CrawlPage> findByJobIdAndStatusAndIncludedTrueOrderById(
            Long jobId, CrawlPage.Status status);
    List<CrawlPage> findByIdInAndJobId(List<Long> ids, Long jobId);
    Page<CrawlPage> findByJobIdOrderById(Long jobId, Pageable pageable);
    Page<CrawlPage> findByJobIdAndUrlContainingIgnoreCaseOrderById(
            Long jobId, String query, Pageable pageable);
    Optional<CrawlPage> findByJobIdAndUrlHash(Long jobId, String urlHash);
    long countByJobId(Long jobId);
    long countByJobIdAndIncludedTrue(Long jobId);
}
