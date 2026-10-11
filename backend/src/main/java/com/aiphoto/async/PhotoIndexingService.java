package com.aiphoto.async;

import com.aiphoto.ai.AiServiceClient;
import com.aiphoto.ai.AiThresholds;
import com.aiphoto.dto.TaskProgressDTO;
import com.aiphoto.entity.AiTask;
import com.aiphoto.entity.Photo;
import com.aiphoto.repository.AiTaskRepository;
import com.aiphoto.repository.PhotoRepository;
import com.aiphoto.service.SettingService;
import com.aiphoto.storage.LocalStorageService;
import com.aiphoto.websocket.ProgressWebSocketHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PhotoIndexingService {
    private final AiServiceClient aiServiceClient;
    private final PhotoRepository photoRepository;
    private final AiTaskRepository aiTaskRepository;
    private final ProgressWebSocketHandler progressHandler;
    private final LocalStorageService storageService;
    private final SettingService settingService;
    private final IndexResultService resultService;
    private final ObjectMapper objectMapper;

    public List<Photo> findPendingPhotos() {
        var models = aiServiceClient.modelStatus();
        return photoRepository.findNeedingIndexing(models.enabled("clip"), models.enabled("insightface"),
                models.enabled("yolo"), models.enabled("blip"));
    }

    @Async
    public void indexPhotos(Long taskId, List<Long> photoIds) {
        indexPhotos(taskId, photoIds, null);
    }

    @Async
    public void indexPhotos(Long taskId, List<Long> photoIds, Long requestingUserId) {
        AiTask task = aiTaskRepository.findById(taskId).orElse(null);
        if (task == null) return;
        task.setStatus(AiTask.TaskStatus.RUNNING);
        aiTaskRepository.save(task);
        List<Map<String, Object>> failures = new ArrayList<>();
        int succeeded = 0;
        try {
            var models = aiServiceClient.modelStatus();
            for (int i = 0; i < photoIds.size(); i++) {
                Long id = photoIds.get(i);
                Map<String, String> errors = new LinkedHashMap<>();
                Photo photo = photoRepository.findById(id).orElse(null);
                if (photo == null) {
                    errors.put("photo", "照片不存在或已删除");
                } else {
                    try {
                        byte[] data = storageService.downloadBytes(photo.getFilePath());
                        String filename = photo.getOriginalFilename();
                        if (models.enabled("clip") && photo.getEmbedding() == null) {
                            stage(errors, "clip", () -> resultService.saveEmbedding(id,
                                    aiServiceClient.embed(data, filename).getEmbedding()));
                        }
                        if (models.enabled("insightface") && !photo.isFacesIndexed()) {
                            stage(errors, "insightface", () -> {
                                Long settingsUser = photo.getAiOwnerId() == null ? requestingUserId : photo.getAiOwnerId();
                                String value = settingsUser == null ? null : settingService.getSetting(
                                        settingsUser, "ai_face_cluster_threshold");
                                double distance = AiThresholds.distance(AiThresholds.similarity(value, 0.5));
                                resultService.saveFaces(id, aiServiceClient.detectFaces(data, filename), distance);
                            });
                        }
                        if (models.enabled("yolo") && !photo.isTagsIndexed()) {
                            stage(errors, "yolo", () -> resultService.saveTags(id,
                                    aiServiceClient.classify(data, filename)));
                        }
                        if (models.enabled("blip") && !photo.isCaptionIndexed()) {
                            stage(errors, "blip", () -> resultService.saveCaption(id,
                                    aiServiceClient.caption(data, filename).getCaption()));
                        }
                    } catch (Exception exception) {
                        errors.put("storage", error(exception));
                    }
                }
                if (errors.isEmpty()) succeeded++;
                else failures.add(Map.of("photoId", id, "stages", errors));
                task.setProgress((i + 1) * 100 / photoIds.size());
                aiTaskRepository.save(task);
                send(task, "已处理 " + (i + 1) + "/" + photoIds.size());
            }
            task.setStatus(failures.isEmpty() ? AiTask.TaskStatus.COMPLETED : AiTask.TaskStatus.FAILED);
            task.setProgress(100);
            task.setResultJson(json(Map.of("total", photoIds.size(), "succeeded", succeeded,
                    "failed", failures.size(), "failures", failures)));
        } catch (Exception exception) {
            log.error("AI indexing failed", exception);
            task.setStatus(AiTask.TaskStatus.FAILED);
            task.setResultJson(json(Map.of("error", error(exception))));
        } finally {
            task.setFinishedAt(LocalDateTime.now());
            aiTaskRepository.save(task);
            send(task, task.getStatus() == AiTask.TaskStatus.COMPLETED
                    ? "AI 分析完成" : "部分 AI 分析失败，成功阶段已保存，可重试未完成阶段");
        }
    }

    private void stage(Map<String, String> errors, String name, Runnable action) {
        try {
            action.run();
        } catch (Exception exception) {
            log.warn("AI stage {} failed: {}", name, error(exception));
            errors.put(name, error(exception));
        }
    }

    private String error(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{\"error\":\"无法保存 AI 任务结果\"}";
        }
    }

    private void send(AiTask task, String message) {
        TaskProgressDTO dto = new TaskProgressDTO();
        dto.setTaskId(task.getId());
        dto.setType(task.getType().name());
        dto.setStatus(task.getStatus().name());
        dto.setProgress(task.getProgress());
        dto.setMessage(message);
        progressHandler.sendProgress(dto);
    }
}
