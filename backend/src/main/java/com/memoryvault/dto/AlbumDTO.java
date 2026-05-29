package com.memoryvault.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AlbumDTO {
    private Long id;
    private String name;
    private String type;
    private Long coverPhotoId;
    private String coverPhotoUrl;
    private Boolean shared;
    private LocalDate birthDate;
    private Integer photoCount;
    private LocalDateTime createdAt;
}
