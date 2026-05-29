package com.memoryvault.async;

import com.memoryvault.ai.AiServiceClient;
import com.memoryvault.config.RabbitMQConfig;
import com.memoryvault.entity.AiTask;
import com.memoryvault.entity.Photo;
import com.memoryvault.repository.AiTaskRepository;
import com.memoryvault.repository.PhotoRepository;
import com.memoryvault.websocket.ProgressWebSocketHandler;
import com.memoryvault.dto.TaskProgressDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PhotoIndexingConsumer {

    private final AiServiceClient aiServiceClient;
    private final PhotoRepository photoRepository;
    private final AiTaskRepository aiTaskRepository;
    private final ProgressWebSocketHandler progressHandler;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_PHOTO_INDEX)
    public void handlePhotoIndex(PhotoIndexMessage message) {
        log.info("Processing photo indexing task: {}", message.getTaskId());

        AiTask task = aiTaskRepository.findById(message.getTaskId()).orElse(null);
        if (task == null) return;

        task.setStatus(AiTask.TaskStatus.RUNNING);
        aiTaskRepository.save(task);

        try {
            List<Long> photoIds = message.getPhotoIds();
            int total = photoIds.size();

            for (int i = 0; i < total; i++) {
                Photo photo = photoRepository.findById(photoIds.get(i)).orElse(null);
                if (photo == null) continue;

                // TODO: Download photo from MinIO and process with AI service
                // 1. CLIP embedding
                // 2. Face detection
                // 3. YOLO classification

                int progress = (int) ((i + 1) * 100.0 / total);
                task.setProgress(progress);
                aiTaskRepository.save(task);

                progressHandler.sendProgress(createProgressDTO(task, "Processing photo " + (i + 1) + "/" + total));
            }

            task.setStatus(AiTask.TaskStatus.COMPLETED);
            aiTaskRepository.save(task);
            progressHandler.sendProgress(createProgressDTO(task, "Completed"));
        } catch (Exception e) {
            log.error("Photo indexing failed", e);
            task.setStatus(AiTask.TaskStatus.FAILED);
            task.setResultJson("{\"error\": \"" + e.getMessage() + "\"}");
            aiTaskRepository.save(task);
        }
    }

    private TaskProgressDTO createProgressDTO(AiTask task, String message) {
        TaskProgressDTO dto = new TaskProgressDTO();
        dto.setTaskId(task.getId());
        dto.setType(task.getType().name());
        dto.setStatus(task.getStatus().name());
        dto.setProgress(task.getProgress());
        dto.setMessage(message);
        return dto;
    }

    @lombok.Data
    public static class PhotoIndexMessage {
        private Long taskId;
        private List<Long> photoIds;
    }
}
