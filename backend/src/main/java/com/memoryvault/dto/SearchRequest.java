package com.memoryvault.dto;

import lombok.Data;

@Data
public class SearchRequest {
    private String query;
    private String type = "text"; // "text" or "semantic"
    private String startDate;
    private String endDate;
    private String location;
    private Long personId;
    private Long tagId;
    private Integer minRating;
    private Integer page = 0;
    private Integer size = 20;
}
