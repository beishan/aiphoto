package com.aiphoto.service;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CrawlerExecutorOptions {
    private String description = "";
    private Long siteId;
    private boolean frozen;
    private String selectionStrategy = "ORDERED";
    private List<ProxyChoice> ordinaryProxies = new ArrayList<>();
    private int defaultProxyCooldownSeconds = 300;
    private int failureThreshold = 2;
    private String manualNode;
    private boolean failover = true;
    private int rotationSeconds = 300;
    private int rotationPages = 50;
    private int rotationImages = 100;
    private int rotationTasks = 1;
    private Long nodeGroupId;
    public record ProxyChoice(Long proxyId, Integer cooldownSeconds) {}
}
