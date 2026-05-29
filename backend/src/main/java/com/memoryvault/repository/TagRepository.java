package com.memoryvault.repository;

import com.memoryvault.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByName(String name);
    List<Tag> findByType(Tag.TagType type);
    List<Tag> findByNameContainingIgnoreCase(String name);
}
