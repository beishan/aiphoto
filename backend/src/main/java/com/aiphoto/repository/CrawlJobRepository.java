package com.aiphoto.repository;

import com.aiphoto.entity.CrawlJob;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CrawlJobRepository extends JpaRepository<CrawlJob, Long> {
    List<CrawlJob> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    @Query("select distinct j from CrawlJob j where j.ownerId = :ownerId and exists "
            + "(select p.id from CrawlPage p where p.jobId = j.id "
            + "and lower(coalesce(p.name, '')) like lower(concat('%', :pageName, '%'))) ")
    List<CrawlJob> searchByOwnerIdAndPageName(
            @Param("ownerId") Long ownerId, @Param("pageName") String pageName);
    Optional<CrawlJob> findByIdAndOwnerId(Long id, Long ownerId);
    List<CrawlJob> findByStatusIn(List<CrawlJob.Status> statuses);
    boolean existsBySiteIdAndStatusIn(Long siteId, Collection<CrawlJob.Status> statuses);
    boolean existsByScanPageIdAndStatusIn(Long scanPageId, Collection<CrawlJob.Status> statuses);

    @Query("select j.id from CrawlJob j where (j.status = com.aiphoto.entity.CrawlJob.Status.QUEUED "
            + "or (j.status = com.aiphoto.entity.CrawlJob.Status.RUNNING "
            + "and (j.leaseUntil is null or j.leaseUntil < :now))) "
            + "and j.phase in (com.aiphoto.entity.CrawlJob.Phase.DISCOVERY, "
            + "com.aiphoto.entity.CrawlJob.Phase.DOWNLOAD, com.aiphoto.entity.CrawlJob.Phase.REVIEW) "
            + "order by coalesce(j.priority, case when j.phase = com.aiphoto.entity.CrawlJob.Phase.DISCOVERY "
            + "or j.phase = com.aiphoto.entity.CrawlJob.Phase.AWAITING_CONFIRMATION "
            + "then 100 else 75 end) desc, j.createdAt asc, j.id asc")
    List<Long> findRunnableJobIds(@Param("now") java.time.LocalDateTime now);

    @Query("select j from CrawlJob j where (j.status = com.aiphoto.entity.CrawlJob.Status.QUEUED "
            + "or (j.status = com.aiphoto.entity.CrawlJob.Status.RUNNING "
            + "and (j.leaseUntil is null or j.leaseUntil < :now))) "
            + "and j.phase in (com.aiphoto.entity.CrawlJob.Phase.DISCOVERY, "
            + "com.aiphoto.entity.CrawlJob.Phase.DOWNLOAD, com.aiphoto.entity.CrawlJob.Phase.REVIEW) "
            + "and coalesce(j.priority, case when j.phase = com.aiphoto.entity.CrawlJob.Phase.DISCOVERY "
            + "or j.phase = com.aiphoto.entity.CrawlJob.Phase.AWAITING_CONFIRMATION "
            + "then 100 else 75 end) > :priority")
    List<CrawlJob> findHigherPriorityRunnableJobs(
            @Param("priority") int priority, @Param("now") java.time.LocalDateTime now);

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
    @Query("UPDATE CrawlJob j SET j.imagesDownloaded = :downloaded WHERE j.id = :id")
    void updateImageProgress(@Param("id") Long id, @Param("downloaded") int downloaded);

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
