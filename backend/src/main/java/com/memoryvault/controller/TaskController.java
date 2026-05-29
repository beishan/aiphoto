package com.memoryvault.controller;

import com.memoryvault.entity.AiTask;
import com.memoryvault.repository.AiTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final AiTaskRepository aiTaskRepository;

    @GetMapping("/{id}")
    public ResponseEntity<AiTask> getTask(@PathVariable Long id) {
        return aiTaskRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
