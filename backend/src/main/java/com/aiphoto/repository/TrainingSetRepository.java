package com.aiphoto.repository;

import com.aiphoto.entity.Album;
import com.aiphoto.entity.TrainingSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TrainingSetRepository extends JpaRepository<TrainingSet, Long> {
    Optional<TrainingSet> findByAlbum(Album album);
    Optional<TrainingSet> findByAlbumId(Long albumId);
}
