package com.memoryvault.dto;

import lombok.Data;

@Data
public class FaceDTO {
    private Long id;
    private Long photoId;
    private String photoUrl;
    private String bboxJson;
    private Double confidence;
    private Long personId;
    private String personName;
}
