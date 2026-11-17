package com.aiphoto.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Explicitly removed labels must not reappear during AI indexing. */
@Data
@NoArgsConstructor
@Entity
@Table(name = "photo_tag_exclusions")
@IdClass(PhotoTagId.class)
public class PhotoTagExclusion {
  @Id @Column(name = "photo_id") private Long photoId;
  @Id @Column(name = "tag_id") private Long tagId;
}
