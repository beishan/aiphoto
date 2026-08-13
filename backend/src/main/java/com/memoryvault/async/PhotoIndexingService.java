package com.memoryvault.async;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memoryvault.ai.AiServiceClient;
import com.memoryvault.dto.TaskProgressDTO;
import com.memoryvault.entity.*;
import com.memoryvault.repository.*;
import com.memoryvault.service.SettingService;
import com.memoryvault.storage.LocalStorageService;
import com.memoryvault.websocket.ProgressWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PhotoIndexingService {

    private final AiServiceClient aiServiceClient;
    private final PhotoRepository photoRepository;
    private final AiTaskRepository aiTaskRepository;
    private final ProgressWebSocketHandler progressHandler;
    private final LocalStorageService storageService;
    private final FaceClusterRepository faceClusterRepository;
    private final PersonRepository personRepository;
    private final TagRepository tagRepository;
    private final PhotoTagRepository photoTagRepository;
    private final SettingService settingService;
    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;

    @Async
    public void indexPhotos(Long taskId, List<Long> photoIds) {
        log.info("Processing photo indexing task: {}", taskId);

        AiTask task = aiTaskRepository.findById(taskId).orElse(null);
        if (task == null) return;

        task.setStatus(AiTask.TaskStatus.RUNNING);
        aiTaskRepository.save(task);

        try {
            int total = photoIds.size();
            int succeeded = 0;
            List<Map<String, Object>> failures = new ArrayList<>();

            for (int i = 0; i < total; i++) {
                Photo photo = photoRepository.findById(photoIds.get(i)).orElse(null);
                if (photo == null) {
                    failures.add(Map.of("photoId", photoIds.get(i), "error", "Photo not found"));
                    continue;
                }

                try {
                    // Read photo from local storage
                    byte[] data = storageService.downloadBytes(photo.getFilePath());

                    // 1. CLIP embedding
                    AiServiceClient.EmbeddingResponse embedResp = aiServiceClient.embed(data, photo.getOriginalFilename());
                    photo.setEmbedding(toFloatArray(embedResp.getEmbedding()));

                    // 2. Face detection with auto-clustering
                    AiServiceClient.FaceDetectionResponse faceResp = aiServiceClient.detectFaces(data, photo.getOriginalFilename());
                    String thresholdStr = settingService.getSetting(1L, "ai_face_cluster_threshold");
                    double faceThreshold = thresholdStr != null ? Double.parseDouble(thresholdStr) / 100.0 : 0.5;

                    for (AiServiceClient.FaceDetectionResponse.FaceResult face : faceResp.getFaces()) {
                        float[] faceEmbedding = toFloatArray(face.getEmbedding());

                        String vectorStr = vectorToString(faceEmbedding);
                        List<Long> similarPersonIds = faceClusterRepository.findPersonIdByVectorSimilarity(
                                vectorStr, faceThreshold, 1);

                        Person person = null;
                        if (!similarPersonIds.isEmpty() && similarPersonIds.get(0) != null) {
                            person = personRepository.findById(similarPersonIds.get(0)).orElse(null);
                            if (person != null) {
                                person.setPhotoCount(person.getPhotoCount() + 1);
                                if (photo.getExifDate() != null) {
                                    if (person.getFirstSeen() == null || photo.getExifDate().isBefore(person.getFirstSeen())) {
                                        person.setFirstSeen(photo.getExifDate());
                                    }
                                    if (person.getLastSeen() == null || photo.getExifDate().isAfter(person.getLastSeen())) {
                                        person.setLastSeen(photo.getExifDate());
                                    }
                                }
                                personRepository.save(person);
                            }
                        }

                        if (person == null) {
                            person = new Person();
                            person.setPhotoCount(1);
                            person.setFirstSeen(photo.getExifDate());
                            person.setLastSeen(photo.getExifDate());
                            person = personRepository.save(person);
                        }

                        FaceCluster fc = new FaceCluster();
                        fc.setPhoto(photo);
                        fc.setPerson(person);
                        fc.setBboxJson(toJson(face.getBbox()));
                        fc.setEmbedding(faceEmbedding);
                        fc.setConfidence(face.getConfidence());
                        faceClusterRepository.save(fc);

                        if (person.getCoverFace() == null) {
                            personRepository.setCoverFaceId(person.getId(), fc.getId());
                        }
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

                    // 3.5 Auto-assign photo to matching system categories
                    autoAssignToCategories(photo, classifyResp);

                    // 4. BLIP-2 caption (lazy-loaded, best effort)
                    try {
                        AiServiceClient.CaptionResponse captionResp = aiServiceClient.caption(data, photo.getOriginalFilename());
                        if (captionResp != null && captionResp.getCaption() != null && !captionResp.getCaption().isBlank()) {
                            photo.setAiCaption(captionResp.getCaption());
                            log.debug("Photo {} caption: {}", photo.getId(), captionResp.getCaption());
                        }
                    } catch (Exception e) {
                        log.warn("BLIP-2 caption failed for photo {}: {}", photo.getId(), e.getMessage());
                    }

                    photoRepository.save(photo);
                    succeeded++;
                } catch (Exception e) {
                    log.error("Failed to process photo {}: {}", photo.getId(), e.getMessage());
                    failures.add(Map.of(
                            "photoId", photo.getId(),
                            "error", safeErrorMessage(e)
                    ));
                }

                int progress = (int) ((i + 1) * 100.0 / total);
                task.setProgress(progress);
                aiTaskRepository.save(task);

                progressHandler.sendProgress(createProgressDTO(task, "Processing photo " + (i + 1) + "/" + total));
            }

            task.setProgress(100);
            task.setFinishedAt(LocalDateTime.now());
            task.setStatus(failures.isEmpty() ? AiTask.TaskStatus.COMPLETED : AiTask.TaskStatus.FAILED);
            task.setResultJson(toResultJson(Map.of(
                    "total", total,
                    "succeeded", succeeded,
                    "failed", failures.size(),
                    "failures", failures
            )));
            aiTaskRepository.save(task);
            String message = failures.isEmpty()
                    ? "Completed"
                    : "Failed: " + failures.size() + " of " + total + " photo(s)";
            progressHandler.sendProgress(createProgressDTO(task, message));
        } catch (Exception e) {
            log.error("Photo indexing failed", e);
            task.setStatus(AiTask.TaskStatus.FAILED);
            task.setFinishedAt(LocalDateTime.now());
            task.setResultJson(toResultJson(Map.of("error", safeErrorMessage(e))));
            aiTaskRepository.save(task);
        }
    }

    private String toResultJson(Object result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            log.warn("Failed to serialize AI task result", exception);
            return "{\"error\":\"Failed to serialize task result\"}";
        }
    }

    private String safeErrorMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static final Map<String, String> YOLO_TO_CATEGORY_ICON = Map.of(
            "person", "person",
            "animal", "animal",
            "food", "food"
    );

    private static final Map<String, String> YOLO_NAME_OVERRIDE = Map.of(
            "potted plant", "plant"
    );

    private void autoAssignToCategories(Photo photo, AiServiceClient.ClassifyResponse classifyResp) {
        try {
            List<Category> systemCategories = categoryRepository.findByIsSystemTrue();
            if (systemCategories.isEmpty()) return;

            java.util.Set<String> matchedIcons = new java.util.HashSet<>();
            for (AiServiceClient.ClassifyResponse.TagResult tag : classifyResp.getTags()) {
                String overrideIcon = YOLO_NAME_OVERRIDE.get(tag.getName());
                if (overrideIcon != null) {
                    matchedIcons.add(overrideIcon);
                    continue;
                }
                String icon = YOLO_TO_CATEGORY_ICON.get(tag.getCategory());
                if (icon != null) {
                    matchedIcons.add(icon);
                }
            }

            for (Category category : systemCategories) {
                if (matchedIcons.contains(category.getIcon())) {
                    if (!categoryRepository.existsPhotoInCategory(category.getId(), photo.getId())) {
                        categoryRepository.addPhotoToCategoryNative(category.getId(), photo.getId());
                        categoryRepository.updatePhotoCount(category.getId());
                        log.debug("Auto-assigned photo {} to category '{}'", photo.getId(), category.getName());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to auto-assign photo {} to categories: {}", photo.getId(), e.getMessage());
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

    private float[] toFloatArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    private String vectorToString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private String toJson(AiServiceClient.FaceDetectionResponse.BBox bbox) {
        return String.format("{\"x\":%.4f,\"y\":%.4f,\"w\":%.4f,\"h\":%.4f}",
                bbox.getX(), bbox.getY(), bbox.getW(), bbox.getH());
    }

    private Tag newTag(String name, String category) {
        Tag tag = new Tag();
        tag.setName(name);
        tag.setType(Tag.TagType.AI);
        tag.setCategory(category);
        return tag;
    }
}
