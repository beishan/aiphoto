package com.aiphoto.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PhotoDetailDTO {
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
    private Boolean inTimeline;
    private String originalFilename;
    private String thumbnailUrl;
    private String originalUrl;
    private LocalDateTime createdAt;
    private Long sourceFolderId;
    private String sourceFolderName;

    private List<TagDTO> tags;
    private List<PersonDTO> people;
    private String fileHashMd5;
    private String fileHashPhash;
}
