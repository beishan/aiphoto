package com.aiphoto.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Data
@NoArgsConstructor
@Entity
@Table(
        name = "crawl_seen_pages",
        uniqueConstraints = @UniqueConstraint(columnNames = {"site_id", "url_hash"}))
public class CrawlSeenPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long siteId;
    @Column(nullable = false, length = 4096)
    private String normalizedUrl;
    @Column(nullable = false, length = 64)
    private String urlHash;
    @CreationTimestamp
    private LocalDateTime firstSeenAt;
}
