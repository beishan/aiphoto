package com.memoryvault.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ScanFolderDTO {
    private Long id;
    private String name;
    private String path;
    private String storageMode;   // COPY / LINK
    private String scanStatus;    // IDLE / SCANNING / COMPLETED / ERROR
    private LocalDateTime lastScanAt;
    private Integer photoCount;
    private String errorMessage;
    private LocalDateTime createdAt;
}
