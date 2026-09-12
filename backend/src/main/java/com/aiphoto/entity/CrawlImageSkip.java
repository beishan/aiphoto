package com.aiphoto.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Data
@NoArgsConstructor
@Entity
@Table(
        name = "crawl_image_skips",
        uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "url_hash"}))
public class CrawlImageSkip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long ownerId;
    @Column(nullable = false, length = 4096)
    private String imageUrl;
    @Column(nullable = false, length = 4096)
    private String normalizedUrl;
    @Column(nullable = false, length = 64)
    private String urlHash;
    @Column(length = 4096)
    private String sourcePageUrl;
    @Column(length = 500)
    private String reason;
    @Column(nullable = false)
    private Long skipCount = 0L;
    private LocalDateTime lastSkippedAt;
    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
