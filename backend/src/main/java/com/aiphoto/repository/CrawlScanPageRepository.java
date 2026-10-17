package com.aiphoto.repository;

import com.aiphoto.entity.CrawlScanPage;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CrawlScanPageRepository extends JpaRepository<CrawlScanPage, Long> {
    List<CrawlScanPage> findByOwnerIdOrderById(Long ownerId);
    List<CrawlScanPage> findByAutoScanEnabledTrue();
    boolean existsBySiteId(Long siteId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CrawlScanPage p where p.id = :id and p.ownerId = :ownerId")
    Optional<CrawlScanPage> lockOwned(Long id, Long ownerId);
}
