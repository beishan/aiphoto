package com.aiphoto.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ModelDownloadService {
    private final ModelCatalogService modelCatalogService;
    private final ModelManagementService modelManagementService;
    private final ModelArtifactService artifactService;
    private final TaskExecutor executor;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.ALWAYS).build();
    private final Map<String, DownloadTask> tasks = new ConcurrentHashMap<>();
    private static final Pattern CONTENT_RANGE = Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)");

    public ModelDownloadService(ModelCatalogService catalog, ModelManagementService management,
            ModelArtifactService artifacts, @Qualifier("modelDownloadExecutor") TaskExecutor executor) {
        this.modelCatalogService = catalog;
        this.modelManagementService = management;
        this.artifactService = artifacts;
        this.executor = executor;
    }

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
        private String filename;
        private String installedPath;
        private volatile long totalSize;
        private volatile long downloadedSize;
        private volatile int progress;
        private volatile DownloadStatus status;
        private volatile String errorMessage;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private transient volatile boolean cancelled;
        private transient volatile boolean paused;
        private transient boolean running;
        private transient boolean reschedule;
        private transient boolean restart;
    }

    public synchronized DownloadTask startDownload(String modelId, String modelName, String typeKey,
            String url, long totalSize) {
        var model = modelCatalogService.getOnlineModels().stream()
                .filter(item -> item.get("id").equals(modelId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("该模型暂不支持在线安装，请配置完整本地模型"));
        for (DownloadTask existing : tasks.values()) {
            if (modelId.equals(existing.getModelId()) && existing.getStatus() != DownloadStatus.FAILED
                    && existing.getStatus() != DownloadStatus.CANCELLED) return existing;
        }
        DownloadTask task = new DownloadTask();
        task.setTaskId("dl-" + UUID.randomUUID());
        task.setModelId(modelId);
        task.setModelName((String) model.get("name"));
        task.setTypeKey((String) model.get("typeKey"));
        task.setUrl((String) model.get("url"));
        task.setFilename((String) model.get("filename"));
        task.setStartTime(LocalDateTime.now());
        task.setStatus(DownloadStatus.PENDING);
        tasks.put(task.getTaskId(), task);
        schedule(task);
        return task;
    }

    public DownloadTask getTask(String taskId) { return tasks.get(taskId); }
    public Map<String, DownloadTask> getAllTasks() { return Map.copyOf(tasks); }

    public DownloadTask pause(String taskId) {
        DownloadTask task = required(taskId);
        synchronized (task) {
            if (task.getStatus() == DownloadStatus.DOWNLOADING || task.getStatus() == DownloadStatus.PENDING) {
                task.setPaused(true);
                task.setStatus(DownloadStatus.PAUSED);
            }
        }
        return task;
    }

    public DownloadTask resume(String taskId) {
        DownloadTask task = required(taskId);
        synchronized (task) {
            if (task.getStatus() == DownloadStatus.PAUSED) {
                task.setPaused(false);
                task.setStatus(DownloadStatus.PENDING);
                if (task.isRunning()) task.setReschedule(true);
                else schedule(task);
            }
        }
        return task;
    }

    public DownloadTask cancel(String taskId) {
        DownloadTask task = required(taskId);
        synchronized (task) {
            if (task.getStatus() != DownloadStatus.COMPLETED && task.getStatus() != DownloadStatus.INSTALLED
                    && task.getStatus() != DownloadStatus.INSTALLING) {
                task.setCancelled(true);
                task.setStatus(DownloadStatus.CANCELLED);
                task.setEndTime(LocalDateTime.now());
            }
        }
        return task;
    }

    public DownloadTask retry(String taskId) {
        DownloadTask task = required(taskId);
        synchronized (task) {
            if (task.isRunning()) throw new IllegalArgumentException("下载正在停止，请稍后重试");
            if (task.getStatus() == DownloadStatus.FAILED || task.getStatus() == DownloadStatus.CANCELLED) {
                task.setCancelled(false);
                task.setPaused(false);
                task.setRestart(true);
                task.setErrorMessage(null);
                task.setStatus(DownloadStatus.PENDING);
                task.setEndTime(null);
                schedule(task);
            }
        }
        return task;
    }

    public DownloadTask setAsCurrent(String taskId) {
        DownloadTask task = required(taskId);
        synchronized (task) {
            if (task.getStatus() != DownloadStatus.COMPLETED && task.getStatus() != DownloadStatus.INSTALLED) {
                throw new IllegalArgumentException("模型尚未下载完成");
            }
            task.setStatus(DownloadStatus.INSTALLING);
            try {
                String name = modelCatalogService.getAiServiceModelName(task.getTypeKey());
                if (name == null) throw new IllegalArgumentException("该模型尚未接入推理服务");
                if (task.getInstalledPath() == null) {
                    Path source = partial(task);
                    task.setInstalledPath(artifactService.install(source, task.getTaskId(), name, task.getFilename()));
                }
                var state = modelManagementService.configure(name, task.getInstalledPath(), true);
                if (state == null || !Boolean.TRUE.equals(state.get("loaded")) || state.get("error") != null) {
                    throw new IllegalStateException(state == null ? "AI 服务未返回加载状态"
                            : state.get("error") == null ? "模型未加载" : String.valueOf(state.get("error")));
                }
                task.setStatus(DownloadStatus.INSTALLED);
                task.setErrorMessage(null);
                task.setEndTime(LocalDateTime.now());
            } catch (Exception exception) {
                task.setStatus(DownloadStatus.COMPLETED);
                task.setErrorMessage(message(exception));
                throw new IllegalArgumentException("模型激活失败: " + message(exception), exception);
            }
        }
        return task;
    }

    private DownloadTask required(String id) {
        DownloadTask task = tasks.get(id);
        if (task == null) throw new IllegalArgumentException("下载任务不存在");
        return task;
    }

    private Path partial(DownloadTask task) throws IOException {
        return modelManagementService.prepareDownloadedTarget(".downloads/" + task.getTaskId() + ".part");
    }

    private void schedule(DownloadTask task) {
        synchronized (task) {
            if (task.isRunning()) return;
            task.setRunning(true);
            try {
                executor.execute(() -> executeDownload(task));
            } catch (RuntimeException exception) {
                task.setRunning(false);
                task.setStatus(DownloadStatus.FAILED);
                task.setErrorMessage("下载队列已满，请稍后重试");
            }
        }
    }

    private void executeDownload(DownloadTask task) {
        try {
            synchronized (task) {
                if (task.isCancelled() || task.isPaused()) return;
                task.setStatus(DownloadStatus.DOWNLOADING);
            }
            Path target = partial(task);
            long offset = !task.isRestart() && Files.exists(target) ? Files.size(target) : 0;
            task.setRestart(false);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(task.getUrl()))
                    .timeout(Duration.ofMinutes(30)).GET();
            if (offset > 0) builder.header("Range", "bytes=" + offset + "-");
            var response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (var input = response.body()) {
                if (response.statusCode() != 200 && response.statusCode() != 206) {
                    throw new IOException("HTTP " + response.statusCode());
                }
                long length = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                boolean append = response.statusCode() == 206;
                if (append) {
                    var range = CONTENT_RANGE.matcher(response.headers().firstValue("Content-Range").orElse(""));
                    if (!range.matches() || Long.parseLong(range.group(1)) != offset) {
                        throw new IOException("断点续传响应范围无效");
                    }
                    task.setTotalSize(Long.parseLong(range.group(3)));
                } else {
                    // A server ignoring Range sends a complete object; truncate instead of appending.
                    offset = 0;
                    task.setTotalSize(length > 0 ? length : 0);
                }
                task.setDownloadedSize(offset);
                try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE,
                        append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING)) {
                    byte[] buffer = new byte[65536];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        if (task.isCancelled() || task.isPaused()) return;
                        output.write(buffer, 0, read);
                        task.setDownloadedSize(task.getDownloadedSize() + read);
                        if (task.getTotalSize() > 0) task.setProgress((int) Math.min(99,
                                task.getDownloadedSize() * 100 / task.getTotalSize()));
                    }
                }
                long actual = Files.size(target);
                if (actual == 0 || (task.getTotalSize() > 0 && actual != task.getTotalSize())) {
                    throw new IOException("下载文件不完整，请重试");
                }
                synchronized (task) {
                    if (task.isCancelled() || task.isPaused()) return;
                    task.setTotalSize(actual);
                    task.setDownloadedSize(actual);
                    task.setProgress(100);
                    task.setStatus(DownloadStatus.COMPLETED);
                    task.setEndTime(LocalDateTime.now());
                }
            }
        } catch (Exception exception) {
            log.warn("Model download failed: {}", task.getModelId(), exception);
            synchronized (task) {
                if (!task.isCancelled() && !task.isPaused()) {
                    task.setStatus(DownloadStatus.FAILED);
                    task.setErrorMessage(message(exception));
                    task.setEndTime(LocalDateTime.now());
                }
            }
        } finally {
            synchronized (task) {
                task.setRunning(false);
                if (task.isReschedule() && task.getStatus() == DownloadStatus.PENDING
                        && !task.isCancelled() && !task.isPaused()) {
                    task.setReschedule(false);
                    schedule(task);
                }
            }
        }
    }

    private String message(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
