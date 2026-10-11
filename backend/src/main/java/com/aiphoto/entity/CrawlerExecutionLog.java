package com.aiphoto.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "crawler_execution_logs")
public class CrawlerExecutionLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long executorId;
    private Long jobId;
    @Column(length = 200)
    private String jobName;
    @Column(length = 200)
    private String siteName;
    @Column(length = 30)
    private String phase;
    @Column(length = 100)
    private String executorName;
    @Column(length = 100)
    private String proxyName;
    @Column(length = 255)
    private String proxyNode;
    @Column(columnDefinition = "TEXT")
    private String url;
    private Boolean failed;
    private Integer attempts;
    private Long durationMillis;
    @Column(length = 500)
    private String description;
    private LocalDateTime createdAt = LocalDateTime.now();
}
