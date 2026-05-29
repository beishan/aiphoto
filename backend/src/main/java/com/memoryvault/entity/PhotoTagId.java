package com.memoryvault.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class PhotoTagId implements Serializable {
    private Long photoId;
    private Long tagId;
}
