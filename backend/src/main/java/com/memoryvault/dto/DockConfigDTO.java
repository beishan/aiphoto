package com.memoryvault.dto;

import lombok.Data;

@Data
public class DockConfigDTO {
    private Double opacity;
    private Integer blurStrength;
    private Integer iconSize;
    private Integer iconPadding;
    private Integer iconGap;
    private Double maxScale;
    private Double animationSpeed;
    private String iconStyle;
}
