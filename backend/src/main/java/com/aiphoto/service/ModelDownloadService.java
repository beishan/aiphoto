package com.aiphoto.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import com.aiphoto.storage.StorageRoots;
import com.aiphoto.storage.StorageRoots.Kind;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ModelDownloadService {
    private final ModelCatalogService modelCatalogService;
    private final ModelManagementService modelManagementService;
    private final ModelArtifactService artifactService;
    private final TaskExecutor executor;
    private final HttpClient httpClient;
    private final ModelDownloadProxyService proxyService;
    private final Map<String, DownloadTask> tasks = new ConcurrentHashMap<>();
    private final ObjectMapper downloadMetadata = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final Pattern TASK_ID = Pattern.compile("dl-[0-9a-f-]{36}");
    private static final Pattern CONTENT_RANGE = Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)");

    public ModelDownloadService(ModelCatalogService catalog, ModelManagementService management,
            ModelArtifactService artifacts, @Qualifier("modelDownloadExecutor") TaskExecutor executor,
            @Value("${MODEL_DOWNLOAD_PROXY:}") String proxyUrl, ModelDownloadProxyService proxyService) {
        this.proxyService = proxyService;
        this.modelCatalogService = catalog;
        this.modelManagementService = management;
        this.artifactService = artifacts;
        this.executor = executor;
        var client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.ALWAYS);
        if (!proxyUrl.isBlank()) {
            URI proxy;
            try {
                proxy = URI.create(proxyUrl.trim());
            } catch (IllegalArgumentException ignored) {
                throw new IllegalArgumentException("MODEL_DOWNLOAD_PROXY must use http://host:port");
            }
            if (!"http".equalsIgnoreCase(proxy.getScheme()) || proxy.getHost() == null
                    || proxy.getPort() < 1 || proxy.getPort() > 65535
                    || proxy.getUserInfo() != null || proxy.getQuery() != null || proxy.getFragment() != null
                    || (!proxy.getPath().isEmpty() && !"/".equals(proxy.getPath()))) {
                throw new IllegalArgumentException("MODEL_DOWNLOAD_PROXY must use http://host:port");
            }
            client.proxy(ProxySelector.of(InetSocketAddress.createUnresolved(proxy.getHost(), proxy.getPort())));
        }
        this.httpClient = client.build();
    }

    public enum DownloadStatus {
        PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED, INSTALLING, INSTALLED
    }

    private record SavedDownload(String modelId, long size, LocalDateTime startTime, LocalDateTime endTime,
            boolean installed) {}

    @PostConstruct
    void restoreCompletedDownloads() {
        try (var storageLease = StorageRoots.writing(Kind.MODELS)) {
            Path directory = modelManagementService.prepareDownloadedTarget(".downloads/.metadata").getParent();
            var catalog = modelCatalogService.getOnlineModels();
            try (var metadata = Files.newDirectoryStream(directory, "*.json")) {
                for (Path file : metadata) {
                    String name = file.getFileName().toString();
                    String taskId = name.substring(0, name.length() - 5);
                    if (!TASK_ID.matcher(taskId).matches() || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                    try {
                        var saved = downloadMetadata.readValue(file.toFile(), SavedDownload.class);
                        var definition = catalog.stream().filter(model -> model.get("id").equals(saved.modelId()))
                                .findFirst().orElse(null);
                        if (definition == null || saved.size() <= 0) continue;
                        DownloadTask task = new DownloadTask();
                        task.setTaskId(taskId);
                        task.setModelId(saved.modelId());
                        task.setModelName((String) definition.get("name"));
                        task.setTypeKey((String) definition.get("typeKey"));
                        task.setUrl((String) definition.get("url"));
                        task.setFilename((String) definition.get("filename"));
                        Path original = partial(task);
                        if (!Files.isRegularFile(original, LinkOption.NOFOLLOW_LINKS) || Files.size(original) != saved.size()) continue;
                        String modelName = modelCatalogService.getAiServiceModelName(task.getTypeKey());
                        if (saved.installed() && modelName != null) {
                            String installed = "installed/" + taskId + "/" + ModelArtifactService.artifactPath(modelName, task.getFilename());
                            if (Files.exists(modelManagementService.resolveModelPath(installed), LinkOption.NOFOLLOW_LINKS)) {
                                task.setInstalledPath(installed);
                            }
                        }
                        task.setTotalSize(saved.size());
                        task.setDownloadedSize(saved.size());
                        task.setProgress(100);
                        task.setStartTime(saved.startTime());
                        task.setEndTime(saved.endTime());
                        task.setStatus(task.getInstalledPath() == null ? DownloadStatus.COMPLETED : DownloadStatus.INSTALLED);
                        tasks.put(taskId, task);
                    } catch (Exception exception) {
                        log.warn("Cannot restore downloaded model {}", taskId, exception);
                    }
                }
            }
        } catch (Exception exception) {
            log.warn("Cannot read saved model downloads", exception);
        }
    }

    private void rememberDownload(DownloadTask task, boolean installed) {
        Path temporary = null;
        try {
            Path target = modelManagementService.prepareDownloadedTarget(".downloads/" + task.getTaskId() + ".json");
            temporary = Files.createTempFile(target.getParent(), ".download-", ".tmp");
            downloadMetadata.writeValue(temporary.toFile(), new SavedDownload(
                    task.getModelId(), task.getTotalSize(), task.getStartTime(), task.getEndTime(), installed));
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            temporary = null;
        } catch (Exception exception) {
            log.warn("Cannot save model download {}", task.getTaskId(), exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException exception) {
                    log.warn("Cannot remove temporary model download metadata", exception);
                }
            }
        }
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
        try (var storageLease = StorageRoots.writing(Kind.MODELS)) {
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

    public synchronized DownloadTask setAsCurrent(String taskId) {
        DownloadTask task = required(taskId);
        if (task.getStatus() == DownloadStatus.INSTALLING) return task;
        if (task.getStatus() != DownloadStatus.COMPLETED && task.getStatus() != DownloadStatus.INSTALLED) {
            throw new IllegalArgumentException("模型尚未下载完成");
        }
        if (tasks.values().stream().anyMatch(item -> item.getStatus() == DownloadStatus.INSTALLING)) {
            throw new IllegalStateException("已有模型正在启用，请稍后重试");
        }
        DownloadStatus previous = task.getStatus();
        task.setErrorMessage(null);
        task.setStatus(DownloadStatus.INSTALLING);
        try {
            executor.execute(() -> activate(task));
        } catch (RuntimeException exception) {
            task.setStatus(previous);
            throw new IllegalStateException("启用队列已满，请稍后重试", exception);
        }
        return task;
    }

    private void activate(DownloadTask task) {
        try (var storageLease = StorageRoots.writing(Kind.MODELS)) {
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
            task.setEndTime(LocalDateTime.now());
            rememberDownload(task, true);
            task.setStatus(DownloadStatus.INSTALLED);
        } catch (Exception exception) {
            log.warn("Model activation failed: {}", task.getModelId(), exception);
            task.setErrorMessage(message(exception));
            task.setStatus(DownloadStatus.COMPLETED);
        }
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

    private void downloadResponse(DownloadTask task, Path target, long offset,
            HttpClient client, HttpRequest request) throws IOException, InterruptedException {
        var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
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
                task.setEndTime(LocalDateTime.now());
                rememberDownload(task, false);
                task.setStatus(DownloadStatus.COMPLETED);
            }
        }
    }

    private void executeDownload(DownloadTask task) {
        try (var storageLease = StorageRoots.writing(Kind.MODELS)) {
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
                try (var selectedClient = proxyService.openSelectedClient()) {
                    HttpClient client = selectedClient == null ? httpClient : selectedClient;
                    downloadResponse(task, target, offset, client, builder.build());
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
    }

    private String message(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
