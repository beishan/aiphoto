package com.aiphoto.repository;

import com.aiphoto.entity.CrawlerExecutionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlerExecutionLogRepository extends JpaRepository<CrawlerExecutionLog, Long> {
    Page<CrawlerExecutionLog> findByExecutorIdOrderByIdDesc(Long executorId, Pageable pageable);
    Page<CrawlerExecutionLog> findByExecutorIdAndFailedTrueOrderByIdDesc(Long executorId, Pageable pageable);
}
