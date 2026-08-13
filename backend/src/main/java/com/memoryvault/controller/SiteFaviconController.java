package com.memoryvault.controller;

import com.memoryvault.dto.SiteFaviconStatusDTO;
import com.memoryvault.service.SiteFaviconService;
import java.util.concurrent.TimeUnit;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/site/favicon")
public class SiteFaviconController {

    private final SiteFaviconService siteFaviconService;

    public SiteFaviconController(SiteFaviconService siteFaviconService) {
        this.siteFaviconService = siteFaviconService;
    }

    @GetMapping("/status")
    public SiteFaviconStatusDTO getStatus() {
        return siteFaviconService.getStatus();
    }

    @GetMapping
    public ResponseEntity<FileSystemResource> getFavicon() {
        FileSystemResource resource = new FileSystemResource(siteFaviconService.getFaviconPath());
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
            .body(resource);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public SiteFaviconStatusDTO upload(@RequestParam("file") MultipartFile file) {
        return siteFaviconService.save(file);
    }

    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    public SiteFaviconStatusDTO restoreDefault() {
        return siteFaviconService.restoreDefault();
    }
}
