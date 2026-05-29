package com.memoryvault.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "ai_tasks")
public class AiTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private TaskType type;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TaskStatus status = TaskStatus.PENDING;

    private Integer progress = 0;

    @Column(columnDefinition = "TEXT")
    private String photoIdsJson;

    @Column(columnDefinition = "TEXT")
    private String resultJson;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;

    public enum TaskType {
        INDEX, TRAIN, DEDUP, CAPTION, BATCH_EMBED
    }

    public enum TaskStatus {
        PENDING, RUNNING, COMPLETED, FAILED
    }
}
