package com.aiphoto.async;

import com.aiphoto.ai.AiServiceClient;
import com.aiphoto.ai.AiThresholds;
import com.aiphoto.dto.TaskProgressDTO;
import com.aiphoto.entity.AiTask;
import com.aiphoto.repository.AiTaskRepository;
import com.aiphoto.storage.LocalStorageService;
import com.aiphoto.websocket.ProgressWebSocketHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrainingTaskService {
    private final AiServiceClient aiServiceClient;
    private final AiTaskRepository aiTaskRepository;
    private final ProgressWebSocketHandler progressHandler;
    private final LocalStorageService storageService;
    private final TrainingResultService resultService;
    private final ObjectMapper objectMapper;

    @Async
    public void trainAlbum(Long taskId, Long albumId, Double threshold) {
        AiTask task = aiTaskRepository.findById(taskId).orElse(null);
        if (task == null) return;
        task.setStatus(AiTask.TaskStatus.RUNNING);
        aiTaskRepository.save(task);
        try {
            double similarity = threshold == null ? 0.75 : threshold;
            AiThresholds.distance(similarity);
            var samples = resultService.samples(albumId);
            float[] centroid = new float[512];
            for (int i = 0; i < samples.size(); i++) {
                var photo = samples.get(i);
                float[] embedding = photo.getEmbedding();
                if (embedding == null) {
                    byte[] data = storageService.downloadBytes(photo.getFilePath());
                    embedding = IndexResultService.vector(aiServiceClient.embed(data, photo.getOriginalFilename()).getEmbedding());
                }
                if (embedding.length != centroid.length) throw new IllegalArgumentException("训练样本向量维度不兼容");
                for (int j = 0; j < centroid.length; j++) centroid[j] += embedding[j] / samples.size();
                task.setProgress((i + 1) * 80 / samples.size());
                aiTaskRepository.save(task);
                send(task, "提取训练样本 " + (i + 1) + "/" + samples.size());
            }
            double norm = 0;
            for (float value : centroid) norm += value * (double) value;
            if (!Double.isFinite(norm) || norm <= 0) throw new IllegalArgumentException("训练样本无法生成有效原型向量");
            for (int j = 0; j < centroid.length; j++) centroid[j] /= (float) Math.sqrt(norm);
            int matched = resultService.complete(albumId, centroid, similarity);
            task.setResultJson(json(Map.of("samples", samples.size(), "matched", matched)));
            task.setStatus(AiTask.TaskStatus.COMPLETED);
            task.setProgress(100);
        } catch (Exception exception) {
            log.error("Album training failed", exception);
            task.setStatus(AiTask.TaskStatus.FAILED);
            task.setResultJson(json(Map.of("error", exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage())));
        } finally {
            task.setFinishedAt(LocalDateTime.now());
            aiTaskRepository.save(task);
            send(task, task.getStatus() == AiTask.TaskStatus.COMPLETED ? "训练完成" : "训练失败");
        }
    }

    private String json(Object result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            return "{\"error\":\"无法保存训练结果\"}";
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