package com.aiphoto.dto;

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
    private Integer maxRating;
    private Boolean hasDescription;
    private Boolean inTimeline;
    private Long folderId;
    private String fileType; // PHOTO, VIDEO, GIF, RAW
    private String sortBy = "date"; // date, rating, name
    private String sortOrder = "desc"; // asc, desc
    private Integer page = 0;
    private Integer size = 20;
}
