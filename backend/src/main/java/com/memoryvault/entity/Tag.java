package com.memoryvault.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "tags")
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(length = 7)
    private String color;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TagType type = TagType.MANUAL;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum TagType {
        MANUAL, AI, SCENE, OBJECT
    }
}
