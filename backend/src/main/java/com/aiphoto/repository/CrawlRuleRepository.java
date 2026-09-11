package com.aiphoto.repository;

import com.aiphoto.entity.CrawlRule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrawlRuleRepository extends JpaRepository<CrawlRule, Long> {
    List<CrawlRule> findByOwnerIdOrderByUpdatedAtDesc(Long ownerId);
    List<CrawlRule> findBySiteIdAndOwnerIdOrderByUpdatedAtDesc(Long siteId, Long ownerId);
    Optional<CrawlRule> findByIdAndOwnerId(Long id, Long ownerId);
    Optional<CrawlRule> findBySiteIdAndOwnerIdAndEnabledTrue(Long siteId, Long ownerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CrawlRule r SET r.enabled = false WHERE r.siteId = :siteId AND r.enabled = true")
    int deactivateAll(@Param("siteId") Long siteId);
}
