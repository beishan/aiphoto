package com.memoryvault.repository;

import com.memoryvault.entity.Category;
import com.memoryvault.entity.Photo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByIsSystemTrue();

    List<Category> findByIsSystemFalse();

    @Query("SELECT p FROM Category c JOIN c.photos p WHERE c.id = :categoryId ORDER BY p.exifDate DESC")
    Page<Photo> findCategoryPhotos(@Param("categoryId") Long categoryId, Pageable pageable);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO photo_categories (category_id, photo_id, source, added_at)
        SELECT :categoryId, p.id, 'auto', NOW()
        FROM photos p
        WHERE p.embedding IS NOT NULL
          AND p.embedding <=> :vector::vector < :threshold
        ON CONFLICT (category_id, photo_id) DO NOTHING
        """, nativeQuery = true)
    int bulkAssignPhotos(@Param("categoryId") Long categoryId,
                         @Param("vector") String vector,
                         @Param("threshold") double threshold);

    @Modifying
    @Transactional
    @Query(value = """
        DELETE FROM photo_categories
        WHERE category_id = :categoryId AND source = 'auto'
        """, nativeQuery = true)
    void clearAutoAssignedPhotos(@Param("categoryId") Long categoryId);

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE categories SET photo_count = (
            SELECT COUNT(*) FROM photo_categories WHERE category_id = :categoryId
        ) WHERE id = :categoryId
        """, nativeQuery = true)
    void updatePhotoCount(@Param("categoryId") Long categoryId);

    @Modifying
    @Transactional
    @Query(value = "UPDATE categories SET cover_photo_id = NULL WHERE cover_photo_id = :photoId", nativeQuery = true)
    void clearCoverPhotoRefs(@Param("photoId") Long photoId);

    @Query(value = "SELECT COUNT(*) > 0 FROM photo_categories WHERE category_id = :categoryId AND photo_id = :photoId", nativeQuery = true)
    boolean existsPhotoInCategory(@Param("categoryId") Long categoryId, @Param("photoId") Long photoId);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO photo_categories (category_id, photo_id, source, added_at)
        VALUES (:categoryId, :photoId, 'auto', NOW())
        ON CONFLICT (category_id, photo_id) DO NOTHING
        """, nativeQuery = true)
    void addPhotoToCategoryNative(@Param("categoryId") Long categoryId, @Param("photoId") Long photoId);
}
