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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long ownerId;
    private Long ruleId;
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
