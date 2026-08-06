package com.memoryvault.repository;

import com.memoryvault.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByName(String name);
    List<Tag> findByType(Tag.TagType type);
    List<Tag> findByNameContainingIgnoreCase(String name);
    List<Tag> findAllByOrderBySortOrderAscCreatedAtDesc();

    @Query("SELECT COUNT(pt) FROM PhotoTag pt WHERE pt.tagId = :tagId")
    long countPhotosByTagId(@Param("tagId") Long tagId);
}
