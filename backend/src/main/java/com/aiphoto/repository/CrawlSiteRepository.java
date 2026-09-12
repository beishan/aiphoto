package com.aiphoto.repository;

import com.aiphoto.entity.CrawlSite;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CrawlSiteRepository extends JpaRepository<CrawlSite, Long> {
    List<CrawlSite> findByOwnerIdOrderByUpdatedAtDesc(Long ownerId);
    Optional<CrawlSite> findByIdAndOwnerId(Long id, Long ownerId);
    List<CrawlSite> findByScheduleEnabledTrue();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE CrawlSite s SET s.lastScheduledScanDate = :today "
            + "WHERE s.id = :siteId AND s.scheduleEnabled = true "
            + "AND (s.lastScheduledScanDate IS NULL OR s.lastScheduledScanDate < :today)")
    int claimDailyScan(@Param("siteId") Long siteId, @Param("today") LocalDate today);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE CrawlSite s SET s.lastScheduledScanDate = NULL "
            + "WHERE s.id = :siteId AND s.lastScheduledScanDate = :today")
    int releaseDailyScan(@Param("siteId") Long siteId, @Param("today") LocalDate today);
}
