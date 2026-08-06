package com.memoryvault.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages asynchronous model downloads with progress tracking.
 * Supports start, pause, resume, cancel, and retry operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelDownloadService {

    private final ModelCatalogService modelCatalogService;

    @Value("${app.ai-model-root:/models}")
    private String modelRoot;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    // In-memory download task storage
    private final Map<String, DownloadTask> tasks = new ConcurrentHashMap<>();

    public enum DownloadStatus {
        PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED, INSTALLING, INSTALLED
    }

    @Data
    public static class DownloadTask {
        private String taskId;
        private String modelId;
        private String modelName;
        private String typeKey;
        private String url;
        private long totalSize;
        private long downloadedSize;
        private int progress;
        private DownloadStatus status;
        private String errorMessage;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private transient volatile boolean cancelled;
        private transient volatile boolean paused;
    }

    /**
     * Start a new download task.
     */
    public DownloadTask startDownload(String modelId, String modelName, String typeKey,
                                       String url, long totalSize) {
        String taskId = "dl-" + System.currentTimeMillis();
        DownloadTask task = new DownloadTask();
        task.setTaskId(taskId);
        task.setModelId(modelId);
        task.setModelName(modelName);
        task.setTypeKey(typeKey);
        task.setUrl(url);
        task.setTotalSize(totalSize);
        task.setDownloadedSize(0);
        task.setProgress(0);
        task.setStatus(DownloadStatus.PENDING);
        task.setStartTime(LocalDateTime.now());
        task.setCancelled(false);
        task.setPaused(false);

        tasks.put(taskId, task);

        // Start async download
        executeDownload(task);

        return task;
    }

    /**
     * Get a download task by ID.
     */
    public DownloadTask getTask(String taskId) {
        return tasks.get(taskId);
    }

    /**
     * Get all download tasks.
     */
    public Map<String, DownloadTask> getAllTasks() {
        return tasks;
    }

    /**
     * Pause a download task.
     */
    public DownloadTask pause(String taskId) {
        DownloadTask task = tasks.get(taskId);
        if (task != null && (task.getStatus() == DownloadStatus.DOWNLOADING || task.getStatus() == DownloadStatus.PENDING)) {
            task.setPaused(true);
            task.setStatus(DownloadStatus.PAUSED);
        }
        return task;
    }

    /**
     * Resume a paused download task.
     */
    public DownloadTask resume(String taskId) {
        DownloadTask task = tasks.get(taskId);
        if (task != null && task.getStatus() == DownloadStatus.PAUSED) {
            task.setPaused(false);
            task.setStatus(DownloadStatus.DOWNLOADING);
            executeDownload(task);
        }
        return task;
    }

    /**
     * Cancel a download task.
     */
    public DownloadTask cancel(String taskId) {
        DownloadTask task = tasks.get(taskId);
        if (task != null && task.getStatus() != DownloadStatus.COMPLETED && task.getStatus() != DownloadStatus.INSTALLED) {
            task.setCancelled(true);
            task.setStatus(DownloadStatus.CANCELLED);
            task.setEndTime(LocalDateTime.now());
        }
        return task;
    }

    /**
     * Retry a failed download task.
     */
    public DownloadTask retry(String taskId) {
        DownloadTask task = tasks.get(taskId);
        if (task != null && (task.getStatus() == DownloadStatus.FAILED || task.getStatus() == DownloadStatus.CANCELLED)) {
            task.setCancelled(false);
            task.setPaused(false);
            task.setDownloadedSize(0);
            task.setProgress(0);
            task.setErrorMessage(null);
            task.setStatus(DownloadStatus.PENDING);
            task.setStartTime(LocalDateTime.now());
            task.setEndTime(null);
            executeDownload(task);
        }
        return task;
    }

    /**
     * Set a downloaded model as the current model.
     */
    public DownloadTask setAsCurrent(String taskId) {
        DownloadTask task = tasks.get(taskId);
        if (task != null && task.getStatus() == DownloadStatus.COMPLETED) {
            task.setStatus(DownloadStatus.INSTALLED);
            task.setEndTime(LocalDateTime.now());
        }
        return task;
    }

    @Async
    protected void executeDownload(DownloadTask task) {
        task.setStatus(DownloadStatus.DOWNLOADING);
        String fileName = task.getModelId() + ".bin";
        Path targetPath = Path.of(modelRoot).toAbsolutePath().resolve(fileName);

        try {
            Files.createDirectories(targetPath.getParent());

            // Check if partial download exists (for resume)
            long existingSize = Files.exists(targetPath) ? Files.size(targetPath) : 0;
            if (existingSize > 0 && existingSize < task.getTotalSize()) {
                task.setDownloadedSize(existingSize);
                task.setProgress((int) ((existingSize * 100) / task.getTotalSize()));
            }

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(task.getUrl()))
                    .GET();

            if (existingSize > 0 && existingSize < task.getTotalSize()) {
                requestBuilder.header("Range", "bytes=" + existingSize + "-");
            }

            HttpResponse<InputStream> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            if (response.statusCode() >= 400) {
                throw new IOException("HTTP " + response.statusCode());
            }

            try (InputStream body = response.body()) {
                // Append mode if resuming
                java.io.FileOutputStream fos = new java.io.FileOutputStream(targetPath.toFile(), existingSize > 0);
                byte[] buffer = new byte[65536];
                int bytesRead;
                AtomicLong totalDownloaded = new AtomicLong(existingSize);

                while ((bytesRead = body.read(buffer)) != -1) {
                    if (task.isCancelled()) {
                        fos.close();
                        task.setStatus(DownloadStatus.CANCELLED);
                        task.setEndTime(LocalDateTime.now());
                        return;
                    }
                    if (task.isPaused()) {
                        fos.write(buffer, 0, bytesRead);
                        fos.flush();
                        fos.close();
                        return; // Will be resumed by resume() call
                    }

                    fos.write(buffer, 0, bytesRead);
                    totalDownloaded.addAndGet(bytesRead);
                    task.setDownloadedSize(totalDownloaded.get());
                    if (task.getTotalSize() > 0) {
                        task.setProgress((int) ((totalDownloaded.get() * 100) / task.getTotalSize()));
                    }
                }
                fos.close();
            }

            // Verify file size
            long actualSize = Files.size(targetPath);
            if (task.getTotalSize() > 0 && actualSize != task.getTotalSize()) {
                log.warn("Downloaded file size mismatch: expected {}, got {}", task.getTotalSize(), actualSize);
            }

            task.setProgress(100);
            task.setStatus(DownloadStatus.COMPLETED);
            task.setEndTime(LocalDateTime.now());
            log.info("Model download completed: {} -> {}", task.getModelId(), targetPath);

        } catch (Exception e) {
            log.error("Model download failed: {}", task.getModelId(), e);
            task.setStatus(DownloadStatus.FAILED);
            task.setErrorMessage(e.getMessage());
            task.setEndTime(LocalDateTime.now());
        }
    }
}
