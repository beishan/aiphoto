package com.memoryvault.repository;

import com.memoryvault.entity.ScanFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ScanFolderRepository extends JpaRepository<ScanFolder, Long> {

    Optional<ScanFolder> findByPath(String path);

    boolean existsByPath(String path);
}
