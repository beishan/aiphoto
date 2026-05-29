package com.memoryvault.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PersonDTO {
    private Long id;
    private String name;
    private Long coverFaceId;
    private String coverPhotoUrl;
    private Integer photoCount;
    private LocalDateTime firstSeen;
    private LocalDateTime lastSeen;
}
