package com.aiphoto.repository;

import com.aiphoto.entity.CrawlJob;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CrawlJobRepository extends JpaRepository<CrawlJob, Long> {
    List<CrawlJob> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    Optional<CrawlJob> findByIdAndOwnerId(Long id, Long ownerId);
    List<CrawlJob> findByStatusIn(List<CrawlJob.Status> statuses);

    @Query(value = "SELECT id FROM crawl_jobs WHERE status = 'QUEUED' "
            + "OR (status = 'RUNNING' AND (lease_until IS NULL OR lease_until < :now)) "
            + "ORDER BY created_at LIMIT 20", nativeQuery = true)
    List<Long> findRunnableJobIds(@Param("now") java.time.LocalDateTime now);

    @Modifying
    @Transactional
    @Query("UPDATE CrawlJob j SET j.status = com.aiphoto.entity.CrawlJob.Status.RUNNING, "
            + "j.leaseOwner = :worker, j.leaseUntil = :until, "
            + "j.attemptCount = j.attemptCount + 1 WHERE j.id = :id AND "
            + "(j.status = com.aiphoto.entity.CrawlJob.Status.QUEUED OR "
            + "(j.status = com.aiphoto.entity.CrawlJob.Status.RUNNING AND "
            + "(j.leaseUntil IS NULL OR j.leaseUntil < :now)))")
    int claim(
            @Param("id") Long id,
            @Param("worker") String worker,
            @Param("now") java.time.LocalDateTime now,
            @Param("until") java.time.LocalDateTime until);

    @Modifying
    @Transactional
    @Query("UPDATE CrawlJob j SET j.leaseUntil = :until WHERE j.id = :id "
            + "AND j.status = com.aiphoto.entity.CrawlJob.Status.RUNNING "
            + "AND j.leaseOwner = :worker")
    int renewLease(
            @Param("id") Long id,
            @Param("worker") String worker,
            @Param("until") java.time.LocalDateTime until);

    @Modifying
    @Transactional
    @Query("UPDATE CrawlJob j SET j.listProcessed = :processed, j.pagesFound = :found WHERE j.id = :id")
    void updateDiscoveryProgress(@Param("id") Long id, @Param("processed") int processed, @Param("found") int found);

    @Modifying
    @Transactional
    @Query("UPDATE CrawlJob j SET j.pagesProcessed = :processed, j.imagesDownloaded = :downloaded, "
            + "j.failCount = :failures WHERE j.id = :id")
    void updateDownloadProgress(
            @Param("id") Long id,
            @Param("processed") int processed,
            @Param("downloaded") int downloaded,
            @Param("failures") int failures);
}
