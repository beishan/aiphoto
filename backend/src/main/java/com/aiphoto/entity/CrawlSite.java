package com.aiphoto.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Data
@NoArgsConstructor
@Entity
@com.fasterxml.jackson.annotation.JsonIgnoreProperties("allowedHosts")
@Table(name = "crawl_sites")
public class CrawlSite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long ownerId;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(nullable = false, length = 2048)
    private String startUrl;
    @Column(columnDefinition = "TEXT")
    private String discoveryUrls;
    @Column(nullable = false, length = 1000)
    private String blockedHosts = "";
    private Integer maxListPages = 100;
    private Integer maxDetailPages = 1000;
    private Integer maxImages = 5000;
    private Long maxFileBytes = 20L * 1024 * 1024;
    private Long minRequestIntervalMillis = 1000L;
    @Column(nullable = false, length = 16)
    private String inspectionProxyMode = "DEFAULT";
    private Long inspectionProxyId;
    private Boolean scheduleEnabled = false;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime dailyScanTime = LocalTime.of(3, 0);
    private LocalDate lastScheduledScanDate;
    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
