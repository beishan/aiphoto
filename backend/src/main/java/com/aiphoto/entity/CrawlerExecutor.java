package com.aiphoto.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "crawler_executors")
public class CrawlerExecutor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false)
    private Boolean enabled = true;
    @Column(nullable = false, length = 16)
    private String proxyMode = "DEFAULT";
    private Long proxyId;
    @Column(length = 255)
    private String proxyGroup;
    @Column(columnDefinition = "TEXT")
    private String nodesJson = "[]";
    @Column(nullable = false, length = 16)
    private String switchMode = "MANUAL";
    @Column(nullable = false)
    private Integer rotateAfterRequests = 100;
    @Column(nullable = false)
    private Integer cooldownSeconds = 300;
    @Column(length = 255)
    private String currentNode;
    @Column(nullable = false)
    private Integer completedRequests = 0;
    @Column(columnDefinition = "TEXT", nullable = false)
    private String cooldownsJson = "{}";
    @Column(length = 500)
    private String lastError;
}
