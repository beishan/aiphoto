package com.aiphoto.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Data
@NoArgsConstructor
@Entity
@Table(name = "photo_sources")
public class PhotoSource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long photoId;
    @Column(nullable = false, length = 4096)
    private String pageUrl;
    @Column(nullable = false, length = 4096)
    private String imageUrl;
    @CreationTimestamp
    private LocalDateTime createdAt;
}
