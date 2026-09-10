package com.aiphoto.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Data
@NoArgsConstructor
@Entity
@Table(name = "crawl_import_batches", uniqueConstraints =
        @UniqueConstraint(columnNames = {"owner_id", "idempotency_key"}))
public class CrawlImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long ownerId;
    @Column(nullable = false)
    private Long jobId;
    @Column(nullable = false, length = 100)
    private String idempotencyKey;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.QUEUED;
    @Column(nullable = false)
    private Integer requestedCount = 0;
    @Column(nullable = false)
    private Integer successCount = 0;
    @Column(nullable = false)
    private Integer failCount = 0;
    @Column(nullable = false)
    private Integer skippedCount = 0;
    private Long albumId;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String assetIdsJson;
    @Column(columnDefinition = "TEXT")
    private String tagIdsJson;
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

    public enum Status { QUEUED, RUNNING, COMPLETED, PARTIAL, FAILED }
}
