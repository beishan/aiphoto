package com.memoryvault.dto;

import lombok.Data;

@Data
public class TagDTO {
    private Long id;
    private String name;
    private String color;
    private String type;
    private String category;
    private Double confidence;
    private String source;
}
