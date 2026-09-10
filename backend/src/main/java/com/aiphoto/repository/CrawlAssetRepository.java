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
    long countByJobIdAndStatus(Long jobId, CrawlAsset.Status status);
    List<CrawlAsset> findByJobIdAndStatus(Long jobId, CrawlAsset.Status status);
    List<CrawlAsset> findByJobIdAndStatusAndFileHashPhashIsNotNullOrderById(
            Long jobId, CrawlAsset.Status status);
    @Modifying
    @Query("UPDATE CrawlAsset a SET a.similarityGroupId = NULL, a.similarityCount = 0 "
            + "WHERE a.jobId = :jobId")
    int clearSimilarityGroups(@Param("jobId") Long jobId);
}
