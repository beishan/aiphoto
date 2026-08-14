package com.memoryvault.repository;

import com.memoryvault.entity.Photo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

    @Override
    @Query("SELECT p FROM Photo p WHERE p.deletedAt IS NULL")
    Page<Photo> findAll(Pageable pageable);

    @Override
    @Query("SELECT p FROM Photo p WHERE p.deletedAt IS NULL")
    List<Photo> findAll();

    @Override
    @Query("SELECT p FROM Photo p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<Photo> findById(@Param("id") Long id);

    @Override
    @Query("SELECT p FROM Photo p WHERE p.deletedAt IS NULL AND p.id IN :ids")
    List<Photo> findAllById(@Param("ids") Iterable<Long> ids);

    @Override
    @Query("SELECT COUNT(p) FROM Photo p WHERE p.deletedAt IS NULL")
    long count();

    @Query("SELECT p FROM Photo p WHERE p.deletedAt IS NULL AND p.mediaType = :mediaType")
    Page<Photo> findByMediaType(@Param("mediaType") Photo.MediaType mediaType, Pageable pageable);

    @Query("SELECT p FROM Photo p WHERE p.deletedAt IS NULL AND p.exifDate BETWEEN :start AND :end ORDER BY p.exifDate DESC")
    List<Photo> findByExifDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT p FROM Photo p WHERE p.deletedAt IS NULL AND p.rating >= :minRating ORDER BY p.rating DESC, p.createdAt DESC")
    Page<Photo> findByMinRating(@Param("minRating") int minRating, Pageable pageable);

    @Query("SELECT p FROM Photo p WHERE p.deletedAt IS NULL AND p.favorite = true ORDER BY p.id DESC")
    Page<Photo> findFavorites(Pageable pageable);

    @Query("SELECT p FROM Photo p WHERE p.deletedAt IS NULL AND p.inTimeline = true AND p.exifDate IS NOT NULL ORDER BY p.exifDate DESC")
    List<Photo> findTimelinePhotos();

    @Query("SELECT p FROM Photo p WHERE p.fileHashMd5 = :hash")
    Optional<Photo> findByFileHashMd5(@Param("hash") String hash);

    @Query(value = "SELECT * FROM photos WHERE deleted_at IS NULL AND embedding <=> CAST(:query_vector AS vector) < :threshold ORDER BY embedding <=> CAST(:query_vector AS vector) LIMIT :limit", nativeQuery = true)
    List<Photo> findByVectorSimilarity(@Param("query_vector") String queryVector, @Param("threshold") double threshold, @Param("limit") int limit);

    @Query(value = "SELECT * FROM photos WHERE deleted_at IS NULL AND to_tsvector('simple', coalesce(note, '') || ' ' || coalesce(ai_caption, '')) @@ plainto_tsquery('simple', :query)", nativeQuery = true)
    Page<Photo> fullTextSearch(@Param("query") String query, Pageable pageable);

    @Query(value = "SELECT EXTRACT(YEAR FROM exif_date) as year, EXTRACT(MONTH FROM exif_date) as month, COUNT(*) as count FROM photos WHERE deleted_at IS NULL AND exif_date IS NOT NULL AND in_timeline = true GROUP BY year, month ORDER BY year DESC, month DESC", nativeQuery = true)
    List<Object[]> getTimelineGrouped();

    @Query(value = "SELECT * FROM photos WHERE deleted_at IS NULL AND file_hash_phash IS NOT NULL", nativeQuery = true)
    List<Photo> findAllWithPhash();

    Optional<Photo> findByFilePath(String filePath);

    @Query("SELECT p FROM Photo p WHERE p.deletedAt IS NULL AND p.embedding IS NULL")
    List<Photo> findByEmbeddingIsNull();

    @Query("SELECT p FROM Photo p WHERE p.deletedAt IS NULL AND p.sourceFolderId = :sourceFolderId")
    Page<Photo> findBySourceFolderId(@Param("sourceFolderId") Long sourceFolderId, Pageable pageable);

    @Query("SELECT COUNT(p) FROM Photo p WHERE p.deletedAt IS NULL AND p.sourceFolderId = :sourceFolderId")
    long countBySourceFolderId(@Param("sourceFolderId") Long sourceFolderId);

    @Query("SELECT p FROM Photo p WHERE p.deletedAt IS NULL AND p.sourceFolderId IN :folderIds ORDER BY p.id DESC")
    Page<Photo> findBySourceFolderIds(@Param("folderIds") List<Long> folderIds, Pageable pageable);

    @Query("SELECT p FROM Photo p WHERE p.deletedAt IS NOT NULL ORDER BY p.deletedAt DESC")
    Page<Photo> findTrash(Pageable pageable);

    @Query("SELECT p FROM Photo p WHERE p.id = :id AND p.deletedAt IS NOT NULL")
    Optional<Photo> findTrashById(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Photo p SET p.deletedAt = NULL WHERE p.deletedAt IS NOT NULL")
    int restoreAllFromTrash();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Photo p SET p.deletedAt = NULL WHERE p.deletedAt IS NOT NULL AND p.id IN :ids")
    int restoreTrashByIds(@Param("ids") List<Long> ids);

    long countByDeletedAtIsNotNull();
}
