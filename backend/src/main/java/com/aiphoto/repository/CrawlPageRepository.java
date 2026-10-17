package com.aiphoto.repository;

import com.aiphoto.entity.CrawlPage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlPageRepository extends JpaRepository<CrawlPage, Long> {
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update CrawlPage p set p.name = :name "
            + "where p.urlHash = :urlHash and p.normalizedUrl = :normalizedUrl "
            + "and (p.name is null or trim(p.name) = '') "
            + "and p.jobId in (select j.id from CrawlJob j where j.siteId = :siteId)")
    int fillMissingNames(Long siteId, String normalizedUrl, String urlHash, String name);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("update CrawlPage p set p.collectionJobId = null where p.collectionJobId = :jobId")
    void clearCollectionJob(Long jobId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from CrawlPage p where p.id = :id")
    Optional<CrawlPage> lockById(Long id);

    @org.springframework.data.jpa.repository.Query("select p from CrawlPage p, CrawlJob j "
            + "where p.jobId = j.id and j.ownerId = :ownerId and j.sourcePageId is null "
            + "and (:siteId is null or j.siteId = :siteId) "
            + "and (lower(p.url) like lower(concat('%', :query, '%')) "
            + "or lower(coalesce(p.name, '')) like lower(concat('%', :query, '%'))) "
            + "and (:pending = false or (p.collectionJobId is null "
            + "and p.status = com.aiphoto.entity.CrawlPage.Status.PENDING "
            + "and (j.phase = com.aiphoto.entity.CrawlJob.Phase.DISCOVERY "
            + "or j.phase = com.aiphoto.entity.CrawlJob.Phase.AWAITING_CONFIRMATION))) "
            + "order by p.id desc")
    Page<CrawlPage> discovered(Long ownerId, Long siteId, String query, boolean pending, Pageable pageable);
    List<CrawlPage> findByJobIdOrderById(Long jobId);
    List<CrawlPage> findByJobIdAndStatusOrderById(Long jobId, CrawlPage.Status status);
    List<CrawlPage> findByJobIdAndStatusAndIncludedTrueOrderById(
            Long jobId, CrawlPage.Status status);
    List<CrawlPage> findByIdInAndJobId(List<Long> ids, Long jobId);
    Page<CrawlPage> findByJobIdOrderById(Long jobId, Pageable pageable);
    @org.springframework.data.jpa.repository.Query("select p from CrawlPage p where p.jobId = :jobId "
            + "and (lower(p.url) like lower(concat('%', :query, '%')) "
            + "or lower(coalesce(p.name, '')) like lower(concat('%', :query, '%'))) order by p.id")
    Page<CrawlPage> searchByJobId(
            Long jobId, String query, Pageable pageable);
    Optional<CrawlPage> findByJobIdAndUrlHash(Long jobId, String urlHash);
    Optional<CrawlPage> findByIdAndJobId(Long id, Long jobId);
    long countByJobId(Long jobId);
    long countByJobIdAndStatusIn(Long jobId, java.util.Collection<CrawlPage.Status> statuses);
    @org.springframework.data.jpa.repository.Query("select distinct p from CrawlPage p, CrawlAsset a "
            + "where p.jobId = :jobId and p.included = true and a.pageId = p.id "
            + "and a.status = com.aiphoto.entity.CrawlAsset.Status.FAILED")
    List<CrawlPage> pagesWithFailedAssets(Long jobId);
    long countByJobIdAndIncludedTrue(Long jobId);
}
