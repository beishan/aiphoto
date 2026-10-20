package com.aiphoto.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Data
@NoArgsConstructor
@Entity
@Table(name = "crawl_jobs")
public class CrawlJob {
    public static final int SCAN_PRIORITY = 100;
    public static final int DOWNLOAD_PRIORITY = 75;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long ownerId;
    private Long siteId;
    private Long ruleId;
    private Long scanPageId;
    private Long sourcePageId;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String ruleSnapshot;
    @Column(nullable = false, length = 200)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Phase phase = Phase.DISCOVERY;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.QUEUED;
    private Integer priority;
    @Column(nullable = false)
    private Boolean priorityCustomized = false;

    public Integer getPriority() {
        if (priority != null) return priority;
        return phase == Phase.DISCOVERY || phase == Phase.AWAITING_CONFIRMATION
                ? SCAN_PRIORITY : DOWNLOAD_PRIORITY;
    }
    private Integer listProcessed = 0;
    private Integer pagesFound = 0;
    private Integer pagesProcessed = 0;
    private Integer imagesDownloaded = 0;
    private Integer failCount = 0;
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    @Column(length = 100)
    @JsonIgnore
    private String leaseOwner;
    @JsonIgnore
    private LocalDateTime leaseUntil;
    @Column(nullable = false)
    private Integer attemptCount = 0;
    @CreationTimestamp
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;

    public enum Phase { DISCOVERY, AWAITING_CONFIRMATION, DOWNLOAD, REVIEW }
    public enum Status { QUEUED, RUNNING, PAUSED, COMPLETED, PARTIAL, FAILED, CANCELLED }
}
