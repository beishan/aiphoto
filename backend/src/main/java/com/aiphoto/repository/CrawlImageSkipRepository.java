package com.aiphoto.repository;

import com.aiphoto.entity.CrawlImageSkip;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlImageSkipRepository extends JpaRepository<CrawlImageSkip, Long> {
    Optional<CrawlImageSkip> findByOwnerIdAndUrlHash(Long ownerId, String urlHash);
    Optional<CrawlImageSkip> findByIdAndOwnerId(Long id, Long ownerId);
    Page<CrawlImageSkip> findByOwnerIdOrderByCreatedAtDesc(Long ownerId, Pageable pageable);
    Page<CrawlImageSkip> findByOwnerIdAndImageUrlContainingIgnoreCaseOrderByCreatedAtDesc(
            Long ownerId, String query, Pageable pageable);
}
