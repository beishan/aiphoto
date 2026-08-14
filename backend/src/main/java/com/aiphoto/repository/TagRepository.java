package com.aiphoto.repository;

import com.aiphoto.entity.Tag;
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

    @Query(value = "SELECT COUNT(*) FROM photo_tags pt JOIN photos p ON p.id = pt.photo_id WHERE pt.tag_id = :tagId AND p.deleted_at IS NULL", nativeQuery = true)
    long countPhotosByTagId(@Param("tagId") Long tagId);
}
