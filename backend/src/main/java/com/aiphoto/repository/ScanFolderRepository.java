package com.aiphoto.repository;

import com.aiphoto.entity.ScanFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScanFolderRepository extends JpaRepository<ScanFolder, Long> {

    Optional<ScanFolder> findByPath(String path);

    boolean existsByPath(String path);

    List<ScanFolder> findByEnabledTrue();
}
