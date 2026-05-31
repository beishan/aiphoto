package com.memoryvault.async;

import com.memoryvault.ai.AiServiceClient;
import com.memoryvault.config.RabbitMQConfig;
import com.memoryvault.entity.*;
import com.memoryvault.repository.*;
import java.util.Map;
import com.memoryvault.service.SettingService;
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
    private final PersonRepository personRepository;
    private final TagRepository tagRepository;
    private final PhotoTagRepository photoTagRepository;
    private final SettingService settingService;
    private final CategoryRepository categoryRepository;

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
                    photo.setEmbedding(toFloatArray(embedResp.getEmbedding()));

                    // 2. Face detection with auto-clustering
                    AiServiceClient.FaceDetectionResponse faceResp = aiServiceClient.detectFaces(data, photo.getOriginalFilename());
                    // Read face clustering threshold from settings
                    String thresholdStr = settingService.getSetting(1L, "ai_face_cluster_threshold");
                    double faceThreshold = thresholdStr != null ? Double.parseDouble(thresholdStr) / 100.0 : 0.5;

                    for (AiServiceClient.FaceDetectionResponse.FaceResult face : faceResp.getFaces()) {
                        float[] faceEmbedding = toFloatArray(face.getEmbedding());

                        // Try to find an existing person with similar face
                        String vectorStr = vectorToString(faceEmbedding);
                        List<FaceCluster> similarFaces = faceClusterRepository.findByVectorSimilarity(
                                vectorStr, faceThreshold, 1);

                        Person person = null;
                        if (!similarFaces.isEmpty()) {
                            // Found a similar face — reuse the same person
                            FaceCluster match = similarFaces.get(0);
                            if (match.getPerson() != null) {
                                person = match.getPerson();
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
                            // No match — create a new person
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

                        // Set cover face for the person if not set
                        if (person.getCoverFace() == null) {
                            person.setCoverFace(fc);
                            personRepository.save(person);
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

                    // 3.5 Auto-assign photo to matching system categories based on YOLO results
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

    /**
     * YOLO category -> system category icon mapping.
     * YOLO returns categories like "person", "animal", "food" etc.
     * We map these to the system category icon field.
     */
    private static final Map<String, String> YOLO_TO_CATEGORY_ICON = Map.of(
            "person", "person",
            "animal", "animal",
            "food", "food"
    );

    // "potted plant" in COCO maps to YOLO category "furniture", but belongs to our "plant" category
    private static final Map<String, String> YOLO_NAME_OVERRIDE = Map.of(
            "potted plant", "plant"
    );

    private void autoAssignToCategories(Photo photo, AiServiceClient.ClassifyResponse classifyResp) {
        try {
            List<Category> systemCategories = categoryRepository.findByIsSystemTrue();
            if (systemCategories.isEmpty()) return;

            // Build a set of matching category icons from YOLO results
            java.util.Set<String> matchedIcons = new java.util.HashSet<>();
            for (AiServiceClient.ClassifyResponse.TagResult tag : classifyResp.getTags()) {
                // Check name override first (e.g., "potted plant" -> "plant")
                String overrideIcon = YOLO_NAME_OVERRIDE.get(tag.getName());
                if (overrideIcon != null) {
                    matchedIcons.add(overrideIcon);
                    continue;
                }
                // Map YOLO category to system category icon
                String icon = YOLO_TO_CATEGORY_ICON.get(tag.getCategory());
                if (icon != null) {
                    matchedIcons.add(icon);
                }
            }

            // Assign photo to matching categories
            for (Category category : systemCategories) {
                if (matchedIcons.contains(category.getIcon())) {
                    if (!category.getPhotos().contains(photo)) {
                        category.getPhotos().add(photo);
                        categoryRepository.save(category);
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
        tag.setType(Tag.TagType.valueOf(category != null ? category.toUpperCase() : "AI"));
        return tag;
    }

    @lombok.Data
    public static class PhotoIndexMessage {
        private Long taskId;
        private List<Long> photoIds;
    }
}
