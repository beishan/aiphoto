package com.memoryvault.controller;

import com.memoryvault.entity.User;
import com.memoryvault.repository.UserRepository;
import com.memoryvault.service.SettingService;
import com.memoryvault.service.ModelManagementService;
import com.memoryvault.service.ModelCatalogService;
import com.memoryvault.service.ModelDownloadService;
import com.memoryvault.service.SystemInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingController {

    private final SettingService settingService;
    private final UserRepository userRepository;
    private final ModelManagementService modelManagementService;
    private final ModelCatalogService modelCatalogService;
    private final ModelDownloadService modelDownloadService;
    private final SystemInfoService systemInfoService;

    @GetMapping
    public ResponseEntity<Map<String, String>> getSettings(Authentication authentication) {
        Long userId = getUserId(authentication);
        return ResponseEntity.ok(settingService.getAllSettings(userId));
    }

    @PutMapping
    public ResponseEntity<Void> updateSettings(Authentication authentication, @RequestBody Map<String, String> settings) {
        Long userId = getUserId(authentication);
        settingService.updateSettings(userId, settings);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> getModels() {
        return ResponseEntity.ok(modelManagementService.getStatus());
    }

    @GetMapping("/models/files")
    public ResponseEntity<List<ModelManagementService.ModelFile>> browseModels(
            @RequestParam(defaultValue = "") String directory) throws IOException {
        return ResponseEntity.ok(modelManagementService.browse(directory));
    }

    @PutMapping("/models/{modelName}")
    public ResponseEntity<Map<String, Object>> configureModel(
            @PathVariable String modelName,
            @RequestBody ModelConfigRequest request) {
        return ResponseEntity.ok(modelManagementService.configure(
                modelName, request.path(), request.enabled()
        ));
    }

    @PostMapping("/models/{modelName}/reload")
    public ResponseEntity<Map<String, Object>> reloadModel(@PathVariable String modelName) {
        return ResponseEntity.ok(modelManagementService.reload(modelName));
    }

    @PostMapping(value = "/models/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ModelManagementService.ModelFile> uploadModel(
            @RequestParam(defaultValue = "") String directory,
            @RequestPart("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(modelManagementService.upload(directory, file));
    }

    // ===== Model Catalog =====

    @GetMapping("/models/catalog")
    public ResponseEntity<List<Map<String, Object>>> getModelCatalog() {
        return ResponseEntity.ok(modelCatalogService.getCatalog());
    }

    @GetMapping("/models/online")
    public ResponseEntity<List<Map<String, Object>>> getOnlineModels() {
        return ResponseEntity.ok(modelCatalogService.getOnlineModels());
    }

    // ===== Model Download Management =====

    @PostMapping("/models/download")
    public ResponseEntity<ModelDownloadService.DownloadTask> startDownload(@RequestBody DownloadRequest request) {
        var onlineModels = modelCatalogService.getOnlineModels();
        var model = onlineModels.stream()
                .filter(m -> m.get("id").equals(request.modelId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Model not found: " + request.modelId()));
        return ResponseEntity.ok(modelDownloadService.startDownload(
                (String) model.get("id"),
                (String) model.get("name"),
                (String) model.get("typeKey"),
                (String) model.get("url"),
                ((Number) model.get("size")).longValue()
        ));
    }

    @GetMapping("/models/downloads")
    public ResponseEntity<Map<String, ModelDownloadService.DownloadTask>> getAllDownloads() {
        return ResponseEntity.ok(modelDownloadService.getAllTasks());
    }

    @GetMapping("/models/downloads/{taskId}")
    public ResponseEntity<ModelDownloadService.DownloadTask> getDownload(@PathVariable String taskId) {
        ModelDownloadService.DownloadTask task = modelDownloadService.getTask(taskId);
        if (task == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(task);
    }

    @PostMapping("/models/downloads/{taskId}/pause")
    public ResponseEntity<ModelDownloadService.DownloadTask> pauseDownload(@PathVariable String taskId) {
        return ResponseEntity.ok(modelDownloadService.pause(taskId));
    }

    @PostMapping("/models/downloads/{taskId}/resume")
    public ResponseEntity<ModelDownloadService.DownloadTask> resumeDownload(@PathVariable String taskId) {
        return ResponseEntity.ok(modelDownloadService.resume(taskId));
    }

    @PostMapping("/models/downloads/{taskId}/cancel")
    public ResponseEntity<ModelDownloadService.DownloadTask> cancelDownload(@PathVariable String taskId) {
        return ResponseEntity.ok(modelDownloadService.cancel(taskId));
    }

    @PostMapping("/models/downloads/{taskId}/retry")
    public ResponseEntity<ModelDownloadService.DownloadTask> retryDownload(@PathVariable String taskId) {
        return ResponseEntity.ok(modelDownloadService.retry(taskId));
    }

    @PostMapping("/models/downloads/{taskId}/set-current")
    public ResponseEntity<ModelDownloadService.DownloadTask> setCurrentModel(@PathVariable String taskId) {
        return ResponseEntity.ok(modelDownloadService.setAsCurrent(taskId));
    }

    // ===== System Info & Storage =====

    @GetMapping("/storage")
    public ResponseEntity<Map<String, Object>> getStorageInfo() {
        return ResponseEntity.ok(systemInfoService.getStorageInfo());
    }

    @GetMapping("/system-info")
    public ResponseEntity<Map<String, Object>> getSystemInfo() {
        return ResponseEntity.ok(systemInfoService.getSystemInfo());
    }

    public record DownloadRequest(String modelId) {
    }

    public record ModelConfigRequest(String path, boolean enabled) {
    }

    private Long getUserId(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getId();
    }
}
