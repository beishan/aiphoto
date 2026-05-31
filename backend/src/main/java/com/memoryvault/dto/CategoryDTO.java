package com.memoryvault.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CategoryDTO {
    private Long id;
    private String name;
    private String icon;
    private String color;
    private Boolean isSystem;
    private Long coverPhotoId;
    private String coverPhotoUrl;
    private Integer photoCount;
    private Boolean trained;
    private LocalDateTime createdAt;
}
