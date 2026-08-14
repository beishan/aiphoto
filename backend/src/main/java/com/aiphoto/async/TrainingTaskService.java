package com.aiphoto.async;

import com.aiphoto.ai.AiServiceClient;
import com.aiphoto.dto.TaskProgressDTO;
import com.aiphoto.entity.*;
import com.aiphoto.repository.*;
import com.aiphoto.storage.LocalStorageService;
import com.aiphoto.websocket.ProgressWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrainingTaskService {

    private final AiServiceClient aiServiceClient;
    private final AlbumRepository albumRepository;
    private final TrainingSetRepository trainingSetRepository;
    private final PhotoRepository photoRepository;
    private final AiTaskRepository aiTaskRepository;
    private final ProgressWebSocketHandler progressHandler;
    private final LocalStorageService storageService;

    @Async
    public void trainAlbum(Long taskId, Long albumId, Double threshold) {
        log.info("Processing training task for album: {}", albumId);

        AiTask task = aiTaskRepository.findById(taskId).orElse(null);
        if (task == null) return;

        task.setStatus(AiTask.TaskStatus.RUNNING);
        aiTaskRepository.save(task);

        try {
            Album album = albumRepository.findById(albumId)
                    .orElseThrow(() -> new RuntimeException("Album not found"));

            List<Photo> positiveSamples = album.getPhotos();
            List<List<Float>> embeddings = new ArrayList<>();

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
            trainingSet.setPrototypeVector(centroid);
            trainingSet.setThreshold(threshold != null ? threshold : 0.75);
            trainingSetRepository.save(trainingSet);

            // Scan all photos and find matches above threshold
            String vectorStr = vectorToString(centroidList);
            double thr = trainingSet.getThreshold();
            List<Photo> allPhotos = photoRepository.findAll();
            int matched = 0;
            for (int i = 0; i < allPhotos.size(); i++) {
                Photo p = allPhotos.get(i);
                if (p.getEmbedding() != null && !album.getPhotos().contains(p)) {
                    List<Photo> similar = photoRepository.findByVectorSimilarity(vectorStr, 1.0 - thr, 1);
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
}
