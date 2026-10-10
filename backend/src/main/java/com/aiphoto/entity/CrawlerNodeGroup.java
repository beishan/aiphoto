package com.aiphoto.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "crawler_node_groups")
public class CrawlerNodeGroup {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long proxyId;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false, length = 255)
    private String controlGroup;
    @Column(nullable = false, length = 255)
    private String host;
    @Column(nullable = false)
    private Integer port;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String nodesJson = "[]";
}
