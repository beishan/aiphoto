package com.memoryvault.repository;

import com.memoryvault.entity.PhotoTag;
import com.memoryvault.entity.PhotoTagId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PhotoTagRepository extends JpaRepository<PhotoTag, PhotoTagId> {
    List<PhotoTag> findByPhotoId(Long photoId);
    List<PhotoTag> findByTagId(Long tagId);
    void deleteByPhotoId(Long photoId);
}
