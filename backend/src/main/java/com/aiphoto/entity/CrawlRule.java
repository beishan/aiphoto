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
@Table(name = "crawl_rules")
public class CrawlRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long ownerId;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(nullable = false, length = 2048)
    private String startUrl;
    @Column(nullable = false, length = 500)
    private String detailSelector;
    @Column(length = 1000)
    private String detailUrlIncludes;
    @Column(length = 1000)
    private String detailUrlExcludes;
    @Column(length = 500)
    private String nextSelector;
    @Column(nullable = false, length = 500)
    private String imageSelector = "img";
    @Column(length = 200)
    private String imageAttributes = "data-original,data-src,srcset,src";
    @Column(length = 1000)
    private String imageUrlIncludes;
    @Column(length = 1000)
    private String imageUrlExcludes;
    @Column(length = 500)
    private String detailNextSelector;
    private Integer maxPagesPerDetail = 20;
    @Column(nullable = false, length = 1000)
    private String allowedHosts;
    private Integer maxListPages = 100;
    private Integer maxDetailPages = 1000;
    private Integer maxImages = 5000;
    private Long maxFileBytes = 20L * 1024 * 1024;
    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
