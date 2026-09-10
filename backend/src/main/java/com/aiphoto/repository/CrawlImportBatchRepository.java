package com.aiphoto.repository;

import com.aiphoto.entity.CrawlImportBatch;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CrawlImportBatchRepository extends JpaRepository<CrawlImportBatch, Long> {
    Optional<CrawlImportBatch> findByOwnerIdAndIdempotencyKey(Long ownerId, String idempotencyKey);
    Optional<CrawlImportBatch> findByIdAndOwnerId(Long id, Long ownerId);

    @Query(value = "SELECT pg_advisory_xact_lock(hashtext(:key))", nativeQuery = true)
    void lockIdempotencyKey(@Param("key") String key);

    @Query(value = "SELECT id FROM crawl_import_batches WHERE status = 'QUEUED' "
            + "OR (status = 'RUNNING' AND (lease_until IS NULL OR lease_until < :now)) "
            + "ORDER BY created_at LIMIT 20", nativeQuery = true)
    java.util.List<Long> findRunnableBatchIds(@Param("now") java.time.LocalDateTime now);

    @Modifying
    @Transactional
    @Query("UPDATE CrawlImportBatch b SET "
            + "b.status = com.aiphoto.entity.CrawlImportBatch.Status.RUNNING, "
            + "b.leaseOwner = :worker, b.leaseUntil = :until, "
            + "b.attemptCount = b.attemptCount + 1 WHERE b.id = :id AND "
            + "(b.status = com.aiphoto.entity.CrawlImportBatch.Status.QUEUED OR "
            + "(b.status = com.aiphoto.entity.CrawlImportBatch.Status.RUNNING AND "
            + "(b.leaseUntil IS NULL OR b.leaseUntil < :now)))")
    int claim(
            @Param("id") Long id,
            @Param("worker") String worker,
            @Param("now") java.time.LocalDateTime now,
            @Param("until") java.time.LocalDateTime until);

    @Modifying
    @Transactional
    @Query("UPDATE CrawlImportBatch b SET b.leaseUntil = :until WHERE b.id = :id "
            + "AND b.status = com.aiphoto.entity.CrawlImportBatch.Status.RUNNING "
            + "AND b.leaseOwner = :worker")
    int renewLease(
            @Param("id") Long id,
            @Param("worker") String worker,
            @Param("until") java.time.LocalDateTime until);
}
