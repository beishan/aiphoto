package com.aiphoto.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

@Data
@Entity
@Table(name = "crawl_scan_pages")
public class CrawlScanPage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long ownerId;
    private Long siteId;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false, length = 2048)
    private String url;
    private Boolean autoScanEnabled = false;
    private Boolean autoCollectEnabled = false;
    private Integer autoCollectPriority = CrawlJob.DOWNLOAD_PRIORITY;
    private Integer scanIntervalMinutes = 360;
    private Integer maxPages = 100;
    private LocalDateTime lastScanAt;
    @CreationTimestamp
    private LocalDateTime createdAt;
}
