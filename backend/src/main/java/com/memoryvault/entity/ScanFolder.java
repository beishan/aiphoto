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
@Table(name = "scan_folders")
public class ScanFolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 1024)
    private String path;

    @Column(nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private StorageMode storageMode = StorageMode.COPY;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ScanStatus scanStatus = ScanStatus.IDLE;

    private LocalDateTime lastScanAt;

    private Integer photoCount = 0;

    private Integer videoCount = 0;

    private Integer fileCount = 0;

    private Integer scanProgress = 0;

    private Boolean enabled = true;

    private Boolean hidden = false;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum StorageMode {
        COPY,   // 复制到本地持久化存储
        LINK    // 只记录原始路径
    }

    public enum ScanStatus {
        IDLE, SCANNING, COMPLETED, ERROR
    }
}
