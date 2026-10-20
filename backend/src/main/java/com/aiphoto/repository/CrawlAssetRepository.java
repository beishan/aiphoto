package com.aiphoto.repository;

import com.aiphoto.entity.CrawlAsset;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

public interface CrawlAssetRepository extends JpaRepository<CrawlAsset, Long> {
    List<CrawlAsset> findByJobIdOrderByIdDesc(Long jobId);
    Page<CrawlAsset> findByJobIdOrderByIdDesc(Long jobId, Pageable pageable);
    Page<CrawlAsset> findByJobIdAndStatusOrderByIdDesc(
            Long jobId, CrawlAsset.Status status, Pageable pageable);
    @Query("SELECT a FROM CrawlAsset a, CrawlAssetSource s WHERE a.id = s.assetId "
            + "AND a.jobId = :jobId AND s.pageId = :pageId ORDER BY a.id DESC")
    Page<CrawlAsset> findByJobIdAndSourcePageId(
            @Param("jobId") Long jobId, @Param("pageId") Long pageId, Pageable pageable);
    @Query("SELECT a FROM CrawlAsset a, CrawlAssetSource s WHERE a.id = s.assetId "
            + "AND a.jobId = :jobId AND s.pageId = :pageId AND a.status = :status "
            + "ORDER BY a.id DESC")
    Page<CrawlAsset> findByJobIdAndSourcePageIdAndStatus(
            @Param("jobId") Long jobId, @Param("pageId") Long pageId,
            @Param("status") CrawlAsset.Status status, Pageable pageable);
    @Query(value = "SELECT a.* FROM crawl_assets a JOIN crawl_jobs j ON j.id = a.job_id "
            + "WHERE j.owner_id = :ownerId AND j.phase = 'REVIEW' "
            + "AND a.status <> 'FAILED' AND (:pageId IS NULL OR j.source_page_id = :pageId) "
            + "AND ((:status = 'ALL' AND a.status <> 'DELETED') OR a.status = :status) "
            + "AND (:importStatus = 'ALL' OR (:importStatus = 'IMPORTED' AND a.imported_photo_id IS NOT NULL) "
            + "OR (:importStatus = 'NOT_IMPORTED' AND a.imported_photo_id IS NULL)) "
            + "AND (:similarOnly = FALSE OR (a.status = 'DOWNLOADED' AND a.similarity_group_id IS NOT NULL)) "
            + "AND (:exactDuplicates = FALSE OR (a.file_hash_md5 IS NOT NULL AND EXISTS "
            + "(SELECT 1 FROM crawl_assets b WHERE b.job_id = a.job_id AND b.status <> 'DELETED' "
            + "AND b.file_hash_md5 = a.file_hash_md5 GROUP BY b.job_id, b.file_hash_md5 HAVING COUNT(*) > 1))) "
            + "ORDER BY a.id DESC",
            countQuery = "SELECT COUNT(*) FROM crawl_assets a JOIN crawl_jobs j ON j.id = a.job_id "
                    + "WHERE j.owner_id = :ownerId AND j.phase = 'REVIEW' "
                    + "AND a.status <> 'FAILED' AND (:pageId IS NULL OR j.source_page_id = :pageId) "
                    + "AND ((:status = 'ALL' AND a.status <> 'DELETED') OR a.status = :status) "
                    + "AND (:importStatus = 'ALL' OR (:importStatus = 'IMPORTED' AND a.imported_photo_id IS NOT NULL) "
                    + "OR (:importStatus = 'NOT_IMPORTED' AND a.imported_photo_id IS NULL)) "
                    + "AND (:similarOnly = FALSE OR (a.status = 'DOWNLOADED' AND a.similarity_group_id IS NOT NULL)) "
                    + "AND (:exactDuplicates = FALSE OR (a.file_hash_md5 IS NOT NULL AND EXISTS "
                    + "(SELECT 1 FROM crawl_assets b WHERE b.job_id = a.job_id AND b.status <> 'DELETED' "
                    + "AND b.file_hash_md5 = a.file_hash_md5 GROUP BY b.job_id, b.file_hash_md5 HAVING COUNT(*) > 1)))",
            nativeQuery = true)
    Page<CrawlAsset> findReviewAssets(
            @Param("ownerId") Long ownerId, @Param("pageId") Long pageId,
            @Param("status") String status, @Param("importStatus") String importStatus,
            @Param("exactDuplicates") boolean exactDuplicates,
            @Param("similarOnly") boolean similarOnly, Pageable pageable);

    @Query(value = "SELECT p.id AS id, p.name AS name, p.url AS url, COUNT(a.id) AS \"assetCount\" "
            + "FROM crawl_jobs j JOIN crawl_pages p ON p.id = j.source_page_id "
            + "JOIN crawl_assets a ON a.job_id = j.id "
            + "WHERE j.owner_id = :ownerId AND j.phase = 'REVIEW' AND j.source_page_id IS NOT NULL "
            + "AND a.status NOT IN ('DELETED', 'FAILED') GROUP BY p.id, p.name, p.url "
            + "ORDER BY MAX(j.created_at) DESC, p.id DESC",
            nativeQuery = true)
    List<ReviewPageGroup> findReviewPageGroups(@Param("ownerId") Long ownerId);
    Page<CrawlAsset> findByJobIdAndStatusAndSimilarityGroupIdIsNotNullOrderByIdDesc(
            Long jobId, CrawlAsset.Status status, Pageable pageable);
    @Query("SELECT a FROM CrawlAsset a WHERE a.jobId = :jobId AND a.status <> :deletedStatus "
            + "AND a.fileHashMd5 IS NOT NULL AND a.fileHashMd5 IN "
            + "(SELECT b.fileHashMd5 FROM CrawlAsset b WHERE b.jobId = :jobId "
            + "AND b.status <> :deletedStatus AND b.fileHashMd5 IS NOT NULL "
            + "GROUP BY b.fileHashMd5 HAVING COUNT(b) > 1) ORDER BY a.id DESC")
    Page<CrawlAsset> findExactDuplicates(
            @Param("jobId") Long jobId,
            @Param("deletedStatus") CrawlAsset.Status deletedStatus,
            Pageable pageable);
    @Query("SELECT a.fileHashMd5, COUNT(a) FROM CrawlAsset a WHERE a.jobId = :jobId "
            + "AND a.status <> :deletedStatus AND a.fileHashMd5 IN :hashes GROUP BY a.fileHashMd5")
    List<Object[]> countHashes(
            @Param("jobId") Long jobId,
            @Param("deletedStatus") CrawlAsset.Status deletedStatus,
            @Param("hashes") List<String> hashes);
    Optional<CrawlAsset> findByIdAndJobId(Long id, Long jobId);
    Optional<CrawlAsset> findByJobIdAndUrlHash(Long jobId, String urlHash);
    List<CrawlAsset> findByIdInAndJobId(List<Long> ids, Long jobId);
    long countByJobId(Long jobId);
    long countByJobIdAndStatus(Long jobId, CrawlAsset.Status status);
    long countByJobIdAndStatusIn(Long jobId, java.util.Collection<CrawlAsset.Status> statuses);
    List<CrawlAsset> findByJobIdAndStatus(Long jobId, CrawlAsset.Status status);
    List<CrawlAsset> findByJobIdAndStatusOrderById(Long jobId, CrawlAsset.Status status);
    List<CrawlAsset> findByJobIdAndStatusOrderByIdDesc(Long jobId, CrawlAsset.Status status);
    List<CrawlAsset> findByJobIdAndStatusAndFileHashPhashIsNotNullOrderById(
            Long jobId, CrawlAsset.Status status);
    @Modifying
    @Query("UPDATE CrawlAsset a SET a.similarityGroupId = NULL, a.similarityCount = 0 "
            + "WHERE a.jobId = :jobId")
    int clearSimilarityGroups(@Param("jobId") Long jobId);

    interface ReviewPageGroup {
        Long getId();
        String getName();
        String getUrl();
        Long getAssetCount();
    }
}
