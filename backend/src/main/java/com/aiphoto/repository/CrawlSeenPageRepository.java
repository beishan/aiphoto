package com.aiphoto.repository;

import com.aiphoto.entity.CrawlSeenPage;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrawlSeenPageRepository extends JpaRepository<CrawlSeenPage, Long> {
    Optional<CrawlSeenPage> findBySiteIdAndUrlHash(Long siteId, String urlHash);
    @Modifying
    @Query(value = "INSERT INTO crawl_seen_pages (site_id, normalized_url, url_hash) "
            + "VALUES (:siteId, :normalizedUrl, :urlHash) "
            + "ON CONFLICT (site_id, url_hash) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(
            @Param("siteId") Long siteId,
            @Param("normalizedUrl") String normalizedUrl,
            @Param("urlHash") String urlHash);
}
