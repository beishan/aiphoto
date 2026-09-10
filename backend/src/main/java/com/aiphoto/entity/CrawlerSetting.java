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
    private Long minRequestIntervalMillis = 1000L;
    @Column(nullable = false)
    private Integer maxRetries = 2;
    @Column(nullable = false)
    private Long retryBaseDelayMillis = 1000L;
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
