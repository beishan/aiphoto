package com.aiphoto.repository;

import com.aiphoto.entity.CrawlRetry;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlRetryRepository extends JpaRepository<CrawlRetry, Long> {
    List<CrawlRetry> findByJobIdOrderByIdDesc(Long jobId);
    Optional<CrawlRetry> findFirstByJobIdAndFinishedAtIsNullOrderByIdDesc(Long jobId);
}
