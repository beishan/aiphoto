package com.memoryvault.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "photo_tags")
@IdClass(PhotoTagId.class)
public class PhotoTag {

    @Id
    @Column(name = "photo_id")
    private Long photoId;

    @Id
    @Column(name = "tag_id")
    private Long tagId;

    private Double confidence;

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    private Tag.TagType source = Tag.TagType.MANUAL;
}
