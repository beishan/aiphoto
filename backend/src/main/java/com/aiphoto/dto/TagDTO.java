package com.aiphoto.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TagDTO {
    private Long id;
    private String name;
    private String color;
    private String type;
    private String category;
    private String description;
    private Integer sortOrder;
    private Integer photoCount;
    private Double confidence;
    private String source;
    private LocalDateTime createdAt;
}
