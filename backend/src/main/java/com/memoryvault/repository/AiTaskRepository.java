package com.memoryvault.repository;

import com.memoryvault.entity.AiTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiTaskRepository extends JpaRepository<AiTask, Long> {
    List<AiTask> findByStatus(AiTask.TaskStatus status);
    List<AiTask> findByType(AiTask.TaskType type);
    List<AiTask> findByStatusIn(List<AiTask.TaskStatus> statuses);
}
