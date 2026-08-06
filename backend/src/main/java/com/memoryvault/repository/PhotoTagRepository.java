package com.memoryvault.repository;

import com.memoryvault.entity.PhotoTag;
import com.memoryvault.entity.PhotoTagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PhotoTagRepository extends JpaRepository<PhotoTag, PhotoTagId> {
    List<PhotoTag> findByPhotoId(Long photoId);
    List<PhotoTag> findByTagId(Long tagId);
    void deleteByPhotoId(Long photoId);
    void deleteByTagId(Long tagId);

    @Query("SELECT pt.photoId FROM PhotoTag pt WHERE pt.tagId = :tagId ORDER BY pt.photoId DESC")
    List<Long> findPhotoIdsByTagId(@Param("tagId") Long tagId);

    @Query("SELECT pt FROM PhotoTag pt WHERE pt.photoId IN :photoIds")
    List<PhotoTag> findByPhotoIdIn(@Param("photoIds") List<Long> photoIds);
}
