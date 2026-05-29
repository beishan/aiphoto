package com.memoryvault.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "photos")
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String filePath;

    @Column(length = 64)
    private String fileHashMd5;

    @Column(length = 64)
    private String fileHashPhash;

    private LocalDateTime exifDate;

    private Double gpsLat;

    private Double gpsLng;

    @Column(length = 5)
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(columnDefinition = "TEXT")
    private String aiCaption;

    /**
     * CLIP 512-dimensional embedding vector stored as float array.
     * In PostgreSQL this maps to a vector(512) column via pgvector.
     */
    @Column(columnDefinition = "vector(512)")
    private String embedding;

    private Integer width;

    private Integer height;

    private Long fileSize;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private MediaType mediaType = MediaType.PHOTO;

    private Boolean favorite = false;

    @Column(length = 255)
    private String originalFilename;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum MediaType {
        PHOTO, VIDEO, GIF, RAW
    }
}
