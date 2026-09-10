package com.aiphoto.repository;

import com.aiphoto.entity.CrawlerSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlerSettingRepository extends JpaRepository<CrawlerSetting, Long> {}
