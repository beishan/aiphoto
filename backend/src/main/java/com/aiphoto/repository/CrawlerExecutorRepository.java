package com.aiphoto.repository;

import com.aiphoto.entity.CrawlerExecutor;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlerExecutorRepository extends JpaRepository<CrawlerExecutor, Long> {
    List<CrawlerExecutor> findAllByOrderByIdAsc();
    boolean existsByProxyId(Long proxyId);
}
