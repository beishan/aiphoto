package com.memoryvault.async;

import com.memoryvault.ai.AiServiceClient;
import com.memoryvault.config.RabbitMQConfig;
import com.memoryvault.entity.*;
import com.memoryvault.repository.*;
import com.memoryvault.storage.MinioStorageService;
import com.memoryvault.websocket.ProgressWebSocketHandler;
import com.memoryvault.dto.TaskProgressDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PhotoIndexingConsumer {

    private final AiServiceClient aiServiceClient;
    private final PhotoRepository photoRepository;
    private final AiTaskRepository aiTaskRepository;
    private final ProgressWebSocketHandler progressHandler;
    private final MinioStorageService storageService;
    private final FaceClusterRepository faceClusterRepository;
    private final TagRepository tagRepository;
    private final PhotoTagRepository photoTagRepository;

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

                try {
                    // Download photo from MinIO
                    byte[] data = storageService.downloadBytes(photo.getFilePath());

                    // 1. CLIP embedding
                    AiServiceClient.EmbeddingResponse embedResp = aiServiceClient.embed(data, photo.getOriginalFilename());
                    photo.setEmbedding(vectorToString(embedResp.getEmbedding()));

                    // 2. Face detection
                    AiServiceClient.FaceDetectionResponse faceResp = aiServiceClient.detectFaces(data, photo.getOriginalFilename());
                    for (AiServiceClient.FaceDetectionResponse.FaceResult face : faceResp.getFaces()) {
                        FaceCluster fc = new FaceCluster();
                        fc.setPhoto(photo);
                        fc.setBboxJson(toJson(face.getBbox()));
                        fc.setEmbedding(vectorToString(face.getEmbedding()));
                        fc.setConfidence(face.getConfidence());
                        faceClusterRepository.save(fc);
                    }

                    // 3. YOLO classification
                    AiServiceClient.ClassifyResponse classifyResp = aiServiceClient.classify(data, photo.getOriginalFilename());
                    for (AiServiceClient.ClassifyResponse.TagResult tag : classifyResp.getTags()) {
                        Tag t = tagRepository.findByName(tag.getName())
                                .orElseGet(() -> tagRepository.save(newTag(tag.getName(), tag.getCategory())));
                        PhotoTag pt = new PhotoTag();
                        pt.setPhotoId(photo.getId());
                        pt.setTagId(t.getId());
                        pt.setConfidence(tag.getConfidence());
                        pt.setSource(Tag.TagType.AI);
                        photoTagRepository.save(pt);
                    }

                    photoRepository.save(photo);
                } catch (Exception e) {
                    log.error("Failed to process photo {}: {}", photo.getId(), e.getMessage());
                }

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

    private String vectorToString(List<Float> vector) {
        return "[" + vector.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")) + "]";
    }

    private String toJson(AiServiceClient.FaceDetectionResponse.BBox bbox) {
        return String.format("{\"x\":%.4f,\"y\":%.4f,\"w\":%.4f,\"h\":%.4f}",
                bbox.getX(), bbox.getY(), bbox.getW(), bbox.getH());
    }

    private Tag newTag(String name, String category) {
        Tag tag = new Tag();
        tag.setName(name);
        tag.setType(Tag.TagType.valueOf(category != null ? category.toUpperCase() : "AI"));
        return tag;
    }

    @lombok.Data
    public static class PhotoIndexMessage {
        private Long taskId;
        private List<Long> photoIds;
    }
}
