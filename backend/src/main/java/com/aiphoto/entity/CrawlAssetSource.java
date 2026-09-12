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
        name = "crawl_asset_sources",
        uniqueConstraints = @UniqueConstraint(columnNames = {"asset_id", "page_id"}))
public class CrawlAssetSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long assetId;
    private Long pageId;
    @Column(nullable = false, length = 4096)
    private String sourcePageUrl;
    @CreationTimestamp
    private LocalDateTime createdAt;
}
