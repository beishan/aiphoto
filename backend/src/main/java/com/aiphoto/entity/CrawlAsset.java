package com.aiphoto.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Data
@NoArgsConstructor
@Entity
@Table(name = "crawl_assets", uniqueConstraints = @UniqueConstraint(columnNames = {"job_id", "url_hash"}))
public class CrawlAsset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long jobId;
    private Long pageId;
    @Column(nullable = false, length = 4096)
    private String sourcePageUrl;
    @Column(nullable = false, length = 4096)
    private String imageUrl;
    @Column(nullable = false, length = 4096)
    private String normalizedUrl;
    @Column(nullable = false, length = 64)
    private String urlHash;
    @Column(length = 1024)
    private String localPath;
    @Column(length = 1024)
    private String thumbnailPath;
    @Column(length = 255)
    private String originalFilename;
    @Column(length = 100)
    private String contentType;
    @Column(length = 64)
    private String fileHashMd5;
    @Column(length = 16)
    private String fileHashPhash;
    private Long similarityGroupId;
    @Column(nullable = false)
    private Integer similarityCount = 0;
    private Long fileSize;
    private Integer width;
    private Integer height;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;
    private Long importedPhotoId;
    @Column(columnDefinition = "TEXT")
    private String note;
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    @Transient
    private Long exactDuplicateCount = 0L;
    @Transient
    private Boolean libraryDuplicate = false;
    @Transient
    private Boolean libraryTrashDuplicate = false;
    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum Status { PENDING, DOWNLOADED, FAILED, SKIPPED, DELETED, DUPLICATE, IMPORTED }
}
