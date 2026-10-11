package com.aiphoto.repository;

import com.aiphoto.entity.Album;
import com.aiphoto.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface AlbumRepository extends JpaRepository<Album, Long> {
    @Query("SELECT DISTINCT a FROM Album a LEFT JOIN FETCH a.photos WHERE a.id = :id")
    Optional<Album> findWithPhotos(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Album a WHERE a.id = :id")
    Optional<Album> findForTraining(@Param("id") Long id);
    List<Album> findByOwner(User owner);
    List<Album> findByType(Album.AlbumType type);
    List<Album> findBySharedTrue();

    @Modifying
    @Transactional
    @Query(value = "UPDATE albums SET cover_photo_id = NULL WHERE cover_photo_id = :photoId", nativeQuery = true)
    void clearCoverPhotoRefs(@Param("photoId") Long photoId);
}
