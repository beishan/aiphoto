package com.aiphoto.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "crawler_executor_receipts", uniqueConstraints = @UniqueConstraint(columnNames = {"executor_id", "receipt_key"}))
public class CrawlerExecutorReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long executorId;
    @Column(nullable = false, length = 150)
    private String receiptKey;
}
