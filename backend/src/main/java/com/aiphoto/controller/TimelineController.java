package com.aiphoto.controller;

import com.aiphoto.dto.PhotoDTO;
import com.aiphoto.service.TimelineService;
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
