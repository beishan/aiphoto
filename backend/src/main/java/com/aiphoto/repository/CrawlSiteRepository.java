package com.aiphoto.repository;

import com.aiphoto.entity.CrawlSite;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlSiteRepository extends JpaRepository<CrawlSite, Long> {
    List<CrawlSite> findByOwnerIdOrderByUpdatedAtDesc(Long ownerId);
    Optional<CrawlSite> findByIdAndOwnerId(Long id, Long ownerId);
}
