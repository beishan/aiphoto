package com.memoryvault.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "face_clusters")
public class FaceCluster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photo_id", nullable = false)
    private Photo photo;

    /**
     * Bounding box as JSON: {"x": 0.1, "y": 0.2, "w": 0.3, "h": 0.4}
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String bboxJson;

    /**
     * InsightFace 512-dimensional embedding vector.
     */
    @Column(columnDefinition = "vector(512)")
    private String embedding;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    private Double confidence;
}
