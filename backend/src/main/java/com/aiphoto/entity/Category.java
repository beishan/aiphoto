package com.aiphoto.entity;

import com.aiphoto.config.PgVectorType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String icon;

    private String color;

    @Column(nullable = false)
    private Boolean isSystem = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cover_photo_id")
    private Photo coverPhoto;

    @Type(PgVectorType.class)
    @Column(columnDefinition = "vector(512)")
    private float[] prototypeVector;

    @Column(nullable = false)
    private Double threshold = 0.7;

    @Column(nullable = false)
    private Integer photoCount = 0;

    @ManyToMany
    @JoinTable(
        name = "photo_categories",
        joinColumns = @JoinColumn(name = "category_id"),
        inverseJoinColumns = @JoinColumn(name = "photo_id")
    )
    private List<Photo> photos = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
