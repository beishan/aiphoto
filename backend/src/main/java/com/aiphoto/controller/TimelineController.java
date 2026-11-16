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

    @GetMapping("/months")
    public ResponseEntity<List<TimelineService.MonthSummary>> months() {
        return ResponseEntity.ok(timelineService.months());
    }

    @GetMapping("/undated/count")
    public Map<String, Long> undatedCount() {
        return Map.of("count", timelineService.undatedCount());
    }

    @GetMapping("/undated")
    public org.springframework.data.domain.Page<PhotoDTO> undated(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "40") int size) {
        return timelineService.undated(page, size);
    }

    @GetMapping("/photos")
    public ResponseEntity<org.springframework.data.domain.Page<PhotoDTO>> month(
            @RequestParam int year, @RequestParam int month,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "60") int size) {
        if (year < 1 || year > 9998 || month < 1 || month > 12) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid month");
        }
        return ResponseEntity.ok(timelineService.month(year, month, page, size));
    }

    @GetMapping
    public ResponseEntity<Map<Integer, Map<Integer, List<PhotoDTO>>>> getTimeline() {
        return ResponseEntity.ok(timelineService.getTimelineGrouped());
    }
}
