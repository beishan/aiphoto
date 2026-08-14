package com.aiphoto.controller;

import com.aiphoto.entity.AiTask;
import com.aiphoto.repository.AiTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final AiTaskRepository aiTaskRepository;

    @GetMapping
    public ResponseEntity<Page<AiTask>> listTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(aiTaskRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AiTask> getTask(@PathVariable Long id) {
        return aiTaskRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
