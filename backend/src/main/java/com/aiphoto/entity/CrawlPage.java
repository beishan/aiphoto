package com.aiphoto.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Data
@NoArgsConstructor
@Entity
@Table(name = "crawl_pages", uniqueConstraints = @UniqueConstraint(columnNames = {"job_id", "url_hash"}))
public class CrawlPage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long jobId;
    @Column(nullable = false, length = 4096)
    private String url;
    @Column(nullable = false, length = 4096)
    private String normalizedUrl;
    @Column(nullable = false, length = 64)
    private String urlHash;
    @Column(nullable = false)
    private Boolean included = true;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum Status { PENDING, RUNNING, SUCCEEDED, FAILED, SKIPPED }
}
