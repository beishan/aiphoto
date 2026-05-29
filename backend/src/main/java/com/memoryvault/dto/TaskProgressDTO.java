package com.memoryvault.dto;

import lombok.Data;

@Data
public class TaskProgressDTO {
    private Long taskId;
    private String type;
    private String status;
    private Integer progress;
    private String message;
}
