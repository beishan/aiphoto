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
        return settingsService.saveProxy(id, request);
    }

    @PutMapping("/proxies/order")
    public List<CrawlerSettingsService.ProxyView> reorder(@RequestBody List<Long> ids) {
        return settingsService.reorder(ids);
    }

    @DeleteMapping("/proxies/{id}")
    public ResponseEntity<Void> deleteProxy(@PathVariable Long id) {
        settingsService.deleteProxy(id);
        return ResponseEntity.noContent().build();
    }
}
