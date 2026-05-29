package com.memoryvault.controller;

import com.memoryvault.entity.Photo;
import com.memoryvault.service.DedupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dedup")
@RequiredArgsConstructor
public class DedupController {

    private final DedupService dedupService;

    @GetMapping("/groups")
    public ResponseEntity<List<List<Photo>>> getDuplicateGroups() {
        return ResponseEntity.ok(dedupService.findExactDuplicates());
    }

    @GetMapping("/similar")
    public ResponseEntity<List<List<Photo>>> getSimilarGroups() {
        return ResponseEntity.ok(dedupService.findSimilarPhotos());
    }

    @PostMapping("/scan")
    public ResponseEntity<String> triggerDedupScan() {
        // TODO: trigger async dedup task via RabbitMQ
        return ResponseEntity.ok("Dedup scan triggered");
    }
}
