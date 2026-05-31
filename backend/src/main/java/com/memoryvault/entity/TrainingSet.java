package com.memoryvault.entity;

import com.memoryvault.config.PgVectorType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "training_sets")
public class TrainingSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id", nullable = false)
    private Album album;

    /**
     * Prototype vector computed from positive samples.
     */
    @Type(PgVectorType.class)
    @Column(columnDefinition = "vector(512)")
    private float[] prototypeVector;

    @Column(nullable = false)
    private Double threshold = 0.75;

    private Integer negativeCount = 0;

    @UpdateTimestamp
    private LocalDateTime trainedAt;
}
