package com.memoryvault.repository;

import com.memoryvault.entity.Photo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

    Page<Photo> findByMediaType(Photo.MediaType mediaType, Pageable pageable);

    @Query("SELECT p FROM Photo p WHERE p.exifDate BETWEEN :start AND :end ORDER BY p.exifDate DESC")
    List<Photo> findByExifDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT p FROM Photo p WHERE p.rating >= :minRating ORDER BY p.rating DESC, p.createdAt DESC")
    Page<Photo> findByMinRating(@Param("minRating") int minRating, Pageable pageable);

    @Query("SELECT p FROM Photo p WHERE p.favorite = true ORDER BY p.createdAt DESC")
    Page<Photo> findFavorites(Pageable pageable);

    @Query("SELECT p FROM Photo p WHERE p.fileHashMd5 = :hash")
    Optional<Photo> findByFileHashMd5(@Param("hash") String hash);

    @Query(value = "SELECT * FROM photos WHERE embedding <=> :query_vector::vector < :threshold ORDER BY embedding <=> :query_vector::vector LIMIT :limit", nativeQuery = true)
    List<Photo> findByVectorSimilarity(@Param("query_vector") String queryVector, @Param("threshold") double threshold, @Param("limit") int limit);

    @Query(value = "SELECT * FROM photos WHERE to_tsvector('simple', coalesce(note, '') || ' ' || coalesce(ai_caption, '')) @@ plainto_tsquery('simple', :query)", nativeQuery = true)
    Page<Photo> fullTextSearch(@Param("query") String query, Pageable pageable);

    @Query(value = "SELECT EXTRACT(YEAR FROM exif_date) as year, EXTRACT(MONTH FROM exif_date) as month, COUNT(*) as count FROM photos WHERE exif_date IS NOT NULL GROUP BY year, month ORDER BY year DESC, month DESC", nativeQuery = true)
    List<Object[]> getTimelineGrouped();

    @Query(value = "SELECT * FROM photos WHERE file_hash_phash IS NOT NULL", nativeQuery = true)
    List<Photo> findAllWithPhash();

    Optional<Photo> findByFilePath(String filePath);
}
