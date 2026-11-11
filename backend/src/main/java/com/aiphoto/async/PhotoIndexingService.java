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

    public record IndexPlan(long maxId, long total, boolean clip, boolean faces, boolean tags, boolean caption) {}

    public IndexPlan planPendingPhotos() {
        var models = aiServiceClient.modelStatus();
        long max = photoRepository.maxPhotoId();
        boolean clip = models.enabled("clip"), faces = models.enabled("insightface");
        boolean tags = models.enabled("yolo"), caption = models.enabled("blip");
        long total = photoRepository.countPendingIndex(0, max, clip, faces, tags, caption);
        return new IndexPlan(max, total, clip, faces, tags, caption);
    }

    @Async
    public void indexPendingPhotos(Long taskId, IndexPlan plan, Long userId) {
        runIndex(taskId, null, userId, plan);
    }

    @Async
    public void indexPhotos(Long taskId, List<Long> photoIds) {
        indexPhotos(taskId, photoIds, null);
    }

    @Async
    public void indexPhotos(Long taskId, List<Long> photoIds, Long requestingUserId) {
        runIndex(taskId, photoIds, requestingUserId, null);
    }

    private void runIndex(Long taskId, List<Long> photoIds, Long requestingUserId, IndexPlan plan) {
        AiTask task = aiTaskRepository.findById(taskId).orElse(null);
        if (task == null) return;
        task.setStatus(AiTask.TaskStatus.RUNNING);
        aiTaskRepository.save(task);
        List<Map<String, Object>> failures = new ArrayList<>();
        long succeeded = 0;
        long failed = 0;
        long processed = 0;
        long total = plan == null ? photoIds.size() : plan.total();
        try {
            var models = aiServiceClient.modelStatus();
            java.util.Iterator<Long> ids = plan == null ? photoIds.iterator() : new java.util.Iterator<>() {
                private long cursor = 0;
                private java.util.Iterator<Long> batch = List.<Long>of().iterator();
                public boolean hasNext() {
                    if (!batch.hasNext()) {
                        batch = photoRepository.pendingIndexIds(cursor, plan.maxId(), plan.clip(), plan.faces(),
                                plan.tags(), plan.caption(), org.springframework.data.domain.PageRequest.of(0, 100)).iterator();
                    }
                    return batch.hasNext();
                }
                public Long next() { Long id = batch.next(); cursor = id; return id; }
            };
            while (ids.hasNext()) {
                Long id = ids.next();
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
                else {
                    failed++;
                    if (failures.size() < 100) failures.add(Map.of("photoId", id, "stages", errors));
                }
                processed++;
                task.setProgress((int) Math.min(99, processed * 100 / Math.max(1, total)));
                if (processed % 10 == 0 || processed == total) {
                    aiTaskRepository.save(task);
                    send(task, "已处理 " + processed + "/" + total);
                }
            }
            task.setStatus(failed == 0 ? AiTask.TaskStatus.COMPLETED : AiTask.TaskStatus.FAILED);
            task.setProgress(100);
            task.setResultJson(json(Map.of("total", processed, "planned", total, "succeeded", succeeded,
                    "failed", failed, "failures", failures, "failuresTruncated", failed > failures.size())));
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
