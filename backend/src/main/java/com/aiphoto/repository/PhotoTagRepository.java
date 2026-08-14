package com.aiphoto.repository;

import com.aiphoto.entity.PhotoTag;
import com.aiphoto.entity.PhotoTagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PhotoTagRepository extends JpaRepository<PhotoTag, PhotoTagId> {
    List<PhotoTag> findByPhotoId(Long photoId);
    List<PhotoTag> findByTagId(Long tagId);
    void deleteByPhotoId(Long photoId);
    void deleteByTagId(Long tagId);

    @Query(value = "SELECT pt.photo_id FROM photo_tags pt JOIN photos p ON p.id = pt.photo_id WHERE pt.tag_id = :tagId AND p.deleted_at IS NULL ORDER BY pt.photo_id DESC", nativeQuery = true)
    List<Long> findPhotoIdsByTagId(@Param("tagId") Long tagId);

    @Query("SELECT pt FROM PhotoTag pt WHERE pt.photoId IN :photoIds")
    List<PhotoTag> findByPhotoIdIn(@Param("photoIds") List<Long> photoIds);
}
