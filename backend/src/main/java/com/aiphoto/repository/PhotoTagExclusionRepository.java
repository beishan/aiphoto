package com.aiphoto.repository;

import com.aiphoto.entity.PhotoTagExclusion;
import com.aiphoto.entity.PhotoTagId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhotoTagExclusionRepository extends JpaRepository<PhotoTagExclusion, PhotoTagId> {}
