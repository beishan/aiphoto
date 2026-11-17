package com.aiphoto.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Entity
@Table(name = "crawl_retries")
public class CrawlRetry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long jobId;
    private String retryType;
    private LocalDateTime queuedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String result = "QUEUED";
    @Column(columnDefinition = "TEXT")
    private String message;
}
