package com.aiphoto.repository;

import com.aiphoto.entity.Album;
import com.aiphoto.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface AlbumRepository extends JpaRepository<Album, Long> {
    List<Album> findByOwner(User owner);
    List<Album> findByType(Album.AlbumType type);
    List<Album> findBySharedTrue();

    @Modifying
    @Transactional
    @Query(value = "UPDATE albums SET cover_photo_id = NULL WHERE cover_photo_id = :photoId", nativeQuery = true)
    void clearCoverPhotoRefs(@Param("photoId") Long photoId);
}
