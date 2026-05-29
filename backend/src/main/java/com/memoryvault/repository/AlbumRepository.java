package com.memoryvault.repository;

import com.memoryvault.entity.Album;
import com.memoryvault.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlbumRepository extends JpaRepository<Album, Long> {
    List<Album> findByOwner(User owner);
    List<Album> findByType(Album.AlbumType type);
    List<Album> findBySharedTrue();
}
