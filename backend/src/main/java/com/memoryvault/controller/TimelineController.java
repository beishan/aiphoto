package com.memoryvault.controller;

import com.memoryvault.dto.PhotoDTO;
import com.memoryvault.service.TimelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/timeline")
@RequiredArgsConstructor
public class TimelineController {

    private final TimelineService timelineService;

    @GetMapping
    public ResponseEntity<Map<Integer, Map<Integer, List<PhotoDTO>>>> getTimeline() {
        return ResponseEntity.ok(timelineService.getTimelineGrouped());
    }
}
