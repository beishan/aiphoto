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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrainingTaskConsumer {

    private final AiServiceClient aiServiceClient;
    private final AlbumRepository albumRepository;
    private final TrainingSetRepository trainingSetRepository;
    private final PhotoRepository photoRepository;
    private final AiTaskRepository aiTaskRepository;
    private final ProgressWebSocketHandler progressHandler;
    private final MinioStorageService storageService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_TRAINING)
    public void handleTrainingTask(TrainingMessage message) {
        log.info("Processing training task for album: {}", message.getAlbumId());

        AiTask task = aiTaskRepository.findById(message.getTaskId()).orElse(null);
        if (task == null) return;

        task.setStatus(AiTask.TaskStatus.RUNNING);
        aiTaskRepository.save(task);

        try {
            Album album = albumRepository.findById(message.getAlbumId())
                    .orElseThrow(() -> new RuntimeException("Album not found"));

            List<Photo> positiveSamples = album.getPhotos();
            List<List<Float>> embeddings = new ArrayList<>();

            // Get embeddings for all positive samples
            for (int i = 0; i < positiveSamples.size(); i++) {
                Photo photo = positiveSamples.get(i);
                byte[] data = storageService.downloadBytes(photo.getFilePath());
                AiServiceClient.EmbeddingResponse response = aiServiceClient.embed(data, photo.getOriginalFilename());
                embeddings.add(response.getEmbedding());

                int progress = (int) ((i + 1) * 50.0 / positiveSamples.size());
                task.setProgress(progress);
                aiTaskRepository.save(task);

                progressHandler.sendProgress(createProgressDTO(task, "Extracting features: " + (i + 1) + "/" + positiveSamples.size()));
            }

            // Compute prototype vector (centroid)
            int dim = embeddings.get(0).size();
            float[] centroid = new float[dim];
            for (List<Float> emb : embeddings) {
                for (int j = 0; j < dim; j++) {
                    centroid[j] += emb.get(j);
                }
            }
            for (int j = 0; j < dim; j++) {
                centroid[j] /= embeddings.size();
            }
            List<Float> centroidList = new ArrayList<>();
            for (float v : centroid) centroidList.add(v);

            // Save training set
            TrainingSet trainingSet = trainingSetRepository.findByAlbumId(album.getId())
                    .orElse(new TrainingSet());
            trainingSet.setAlbum(album);
            trainingSet.setPrototypeVector(vectorToString(centroidList));
            trainingSet.setThreshold(message.getThreshold() != null ? message.getThreshold() : 0.75);
            trainingSetRepository.save(trainingSet);

            // Scan all photos and find matches above threshold
            String vectorStr = vectorToString(centroidList);
            double threshold = trainingSet.getThreshold();
            List<Photo> allPhotos = photoRepository.findAll();
            int matched = 0;
            for (int i = 0; i < allPhotos.size(); i++) {
                Photo p = allPhotos.get(i);
                if (p.getEmbedding() != null && !album.getPhotos().contains(p)) {
                    List<Photo> similar = photoRepository.findByVectorSimilarity(vectorStr, 1.0 - threshold, 1);
                    if (!similar.isEmpty() && similar.get(0).getId().equals(p.getId())) {
                        album.getPhotos().add(p);
                        matched++;
                    }
                }
                int progress = 50 + (int) ((i + 1) * 50.0 / allPhotos.size());
                task.setProgress(progress);
                if (i % 10 == 0) {
                    aiTaskRepository.save(task);
                    progressHandler.sendProgress(createProgressDTO(task, "Scanning: " + (i + 1) + "/" + allPhotos.size()));
                }
            }
            albumRepository.save(album);

            task.setStatus(AiTask.TaskStatus.COMPLETED);
            task.setProgress(100);
            aiTaskRepository.save(task);
            progressHandler.sendProgress(createProgressDTO(task, "Training completed"));
        } catch (Exception e) {
            log.error("Training task failed", e);
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

    @lombok.Data
    public static class TrainingMessage {
        private Long taskId;
        private Long albumId;
        private Double threshold;
    }
}
