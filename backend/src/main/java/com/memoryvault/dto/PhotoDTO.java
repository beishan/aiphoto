package com.memoryvault.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PhotoDTO {
    private Long id;
    private String filePath;
    private LocalDateTime exifDate;
    private Double gpsLat;
    private Double gpsLng;
    private Integer rating;
    private String note;
    private String aiCaption;
    private Integer width;
    private Integer height;
    private Long fileSize;
    private String mediaType;
    private Boolean favorite;
    private String originalFilename;
    private String thumbnailUrl;
    private String originalUrl;
    private List<String> tags;
    private LocalDateTime createdAt;
}
