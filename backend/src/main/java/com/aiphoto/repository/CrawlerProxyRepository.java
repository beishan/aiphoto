package com.aiphoto.repository;

import com.aiphoto.entity.CrawlerProxy;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlerProxyRepository extends JpaRepository<CrawlerProxy, Long> {
    List<CrawlerProxy> findAllByOrderByPriorityAscIdAsc();
    List<CrawlerProxy> findByEnabledTrueOrderByPriorityAscIdAsc();
}
