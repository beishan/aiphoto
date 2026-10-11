package com.aiphoto.repository;

import com.aiphoto.entity.CrawlerExecutorReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlerExecutorReceiptRepository extends JpaRepository<CrawlerExecutorReceipt, Long> {
    boolean existsByExecutorIdAndReceiptKey(Long executorId, String receiptKey);
}
