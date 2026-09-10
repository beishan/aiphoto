package com.aiphoto.repository;

import com.aiphoto.entity.CrawlRule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlRuleRepository extends JpaRepository<CrawlRule, Long> {
    List<CrawlRule> findByOwnerIdOrderByUpdatedAtDesc(Long ownerId);
    Optional<CrawlRule> findByIdAndOwnerId(Long id, Long ownerId);
}
