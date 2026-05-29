package com.memoryvault.repository;

import com.memoryvault.entity.Album;
import com.memoryvault.entity.TrainingSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TrainingSetRepository extends JpaRepository<TrainingSet, Long> {
    Optional<TrainingSet> findByAlbum(Album album);
    Optional<TrainingSet> findByAlbumId(Long albumId);
}
