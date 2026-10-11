package com.aiphoto.repository;

import com.aiphoto.entity.CrawlerNodeGroup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlerNodeGroupRepository extends JpaRepository<CrawlerNodeGroup, Long> {
    List<CrawlerNodeGroup> findByProxyIdOrderByIdAsc(Long proxyId);
    boolean existsByProxyId(Long proxyId);
}
