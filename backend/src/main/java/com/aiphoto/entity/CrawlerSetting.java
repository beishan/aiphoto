package com.aiphoto.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

@Data
@NoArgsConstructor
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "crawler_settings")
public class CrawlerSetting {

    @Id
    private Long id = 1L;
    @Column(nullable = false)
    private Boolean directFallback = true;
    @Column(nullable = false)
    private Integer connectTimeoutSeconds = 10;
    @Column(nullable = false)
    private Integer requestTimeoutSeconds = 30;
    @Column(nullable = false)
    private Integer imageDownloadTimeoutSeconds = 60;
    @Column(nullable = false)
    private Long minRequestIntervalMillis = 1000L;
    @Column(nullable = false)
    private Integer maxRetries = 2;
    @Column(nullable = false)
    private Long retryBaseDelayMillis = 1000L;
    @Column(nullable = false)
    private Integer scanTaskDefaultPriority = 100;
    @Column(nullable = false)
    private Integer collectionTaskDefaultPriority = 75;
    private Boolean taskAutoRetryEnabled = false;
    private Integer taskRetryIntervalSeconds = 300;
    private Integer taskMaxRetries = 3;
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private com.aiphoto.service.CrawlDiskGuard.Mode diskLimitMode =
            com.aiphoto.service.CrawlDiskGuard.Mode.OFF;
    private double diskLimitValue;
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
