package com.aiphoto.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

@Data
@Entity
@Table(name = "crawl_scan_results", uniqueConstraints = @UniqueConstraint(columnNames = {"job_id", "url_hash"}))
public class CrawlScanResult {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long jobId;
    @Column(length = 4096, nullable = false)
    private String url;
    @Column(length = 64, nullable = false)
    private String urlHash;
    @Column(length = 1000)
    private String name;
    @Column(nullable = false, length = 20)
    private String result;
    private int occurrences = 1;
    @CreationTimestamp
    private LocalDateTime createdAt;
}
