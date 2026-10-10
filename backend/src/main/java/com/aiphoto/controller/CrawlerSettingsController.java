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
        executorService.assertProxyUpdateAllowed(id, request);
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

    @PostMapping("/proxies/test")
    public com.aiphoto.service.CrawlerProxyTestService.TestResult previewProxy(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long id,
            @RequestBody CrawlerSettingsService.ProxyRequest request) {
        return proxyTestService.preview(id, request);
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

    @PutMapping("/executors/{id}/freeze")
    public com.aiphoto.service.CrawlerExecutorService.ExecutorView freeze(@PathVariable Long id, @RequestBody FreezeRequest request) throws Exception {
        return executorService.freeze(id, request.frozen());
    }
    @PostMapping("/executors/{id}/unbind")
    public com.aiphoto.service.CrawlerExecutorService.ExecutorView unbind(@PathVariable Long id) throws Exception { return executorService.unbind(id); }
    @GetMapping("/executors/{id}/logs")
    public org.springframework.data.domain.Page<com.aiphoto.entity.CrawlerExecutionLog> logs(@PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean failedOnly) {
        return executorService.logs(id, page, failedOnly);
    }
    @GetMapping("/proxies/{id}/groups")
    public List<com.aiphoto.service.CrawlerExecutorService.GroupView> groups(@PathVariable Long id) { return executorService.groups(id); }
    @PostMapping("/proxies/{id}/groups")
    public com.aiphoto.service.CrawlerExecutorService.GroupView createGroup(@PathVariable Long id,
            @RequestBody com.aiphoto.service.CrawlerExecutorService.GroupRequest request) throws Exception { return executorService.saveGroup(id, null, request); }
    @PutMapping("/proxies/{id}/groups/{groupId}")
    public com.aiphoto.service.CrawlerExecutorService.GroupView updateGroup(@PathVariable Long id, @PathVariable Long groupId,
            @RequestBody com.aiphoto.service.CrawlerExecutorService.GroupRequest request) throws Exception { return executorService.saveGroup(id, groupId, request); }
    @DeleteMapping("/proxies/{id}/groups/{groupId}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id, @PathVariable Long groupId) { executorService.deleteGroup(id, groupId); return ResponseEntity.noContent().build(); }
    public record FreezeRequest(boolean frozen) {}

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
