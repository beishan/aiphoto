package com.aiphoto.controller;

import com.aiphoto.service.CrawlerSettingsService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/crawler")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CrawlerSettingsController {

    private final CrawlerSettingsService settingsService;
    private final com.aiphoto.service.CrawlerExecutorService executorService;
    private final com.aiphoto.service.CrawlerProxyTestService proxyTestService;

    @GetMapping
    public CrawlerSettingsService.SettingsView getSettings() {
        return settingsService.getSettings();
    }

    @PutMapping
    public CrawlerSettingsService.SettingsView updateSettings(
            @RequestBody CrawlerSettingsService.SettingsRequest request) {
        return settingsService.updateSettings(request);
    }

    @PostMapping("/proxies")
    public CrawlerSettingsService.ProxyView createProxy(
            @RequestBody CrawlerSettingsService.ProxyRequest request) {
        return settingsService.saveProxy(null, request);
    }

    @PutMapping("/proxies/{id}")
    public CrawlerSettingsService.ProxyView updateProxy(
            @PathVariable Long id,
            @RequestBody CrawlerSettingsService.ProxyRequest request) {
        executorService.assertProxyEditable(id);
        return settingsService.saveProxy(id, request);
    }

    @PutMapping("/proxies/order")
    public List<CrawlerSettingsService.ProxyView> reorder(@RequestBody List<Long> ids) {
        return settingsService.reorder(ids);
    }

    @PostMapping("/proxies/{id}/test")
    public com.aiphoto.service.CrawlerProxyTestService.TestResult testProxy(@PathVariable Long id) {
        return proxyTestService.test(id);
    }

    @GetMapping("/proxies/{id}/nodes")
    public com.fasterxml.jackson.databind.JsonNode nodes(@PathVariable Long id) throws Exception {
        return executorService.nodes(id);
    }

    @PostMapping("/proxies/{id}/delay")
    public java.util.Map<String, Integer> delay(@PathVariable Long id, @RequestBody NodeRequest request) throws Exception {
        return java.util.Map.of("delay", executorService.delay(id, request.node()));
    }

    @GetMapping("/executors")
    public List<com.aiphoto.service.CrawlerExecutorService.ExecutorView> executors() {
        return executorService.list();
    }

    @PostMapping("/executors")
    public com.aiphoto.service.CrawlerExecutorService.ExecutorView createExecutor(
            @RequestBody com.aiphoto.service.CrawlerExecutorService.ExecutorRequest request) throws Exception {
        return executorService.save(null, request);
    }

    @PutMapping("/executors/{id}")
    public com.aiphoto.service.CrawlerExecutorService.ExecutorView updateExecutor(@PathVariable Long id,
            @RequestBody com.aiphoto.service.CrawlerExecutorService.ExecutorRequest request) throws Exception {
        return executorService.save(id, request);
    }

    @DeleteMapping("/executors/{id}")
    public ResponseEntity<Void> deleteExecutor(@PathVariable Long id) {
        executorService.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record NodeRequest(String node) {}

    @DeleteMapping("/proxies/{id}")
    public ResponseEntity<Void> deleteProxy(@PathVariable Long id) {
        executorService.assertProxyEditable(id);
        settingsService.deleteProxy(id);
        return ResponseEntity.noContent().build();
    }
}
