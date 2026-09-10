package com.aiphoto.repository;

import com.aiphoto.entity.PhotoSource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhotoSourceRepository extends JpaRepository<PhotoSource, Long> {}
