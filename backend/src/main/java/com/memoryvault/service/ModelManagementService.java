package com.memoryvault.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ModelManagementService {

    private final RestTemplate restTemplate;

    @Value("${app.ai-service-url}")
    private String aiServiceUrl;

    @Value("${app.ai-model-root:/models}")
    private String configuredRoot;

    public Map<String, Object> getStatus() {
        return restTemplate.getForObject(aiServiceUrl + "/models", Map.class);
    }

    public Map<String, Object> configure(String modelName, String path, boolean enabled) {
        ResponseEntity<Map> response = restTemplate.exchange(
                aiServiceUrl + "/models/{name}",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("path", path, "enabled", enabled)),
                Map.class,
                modelName
        );
        return response.getBody();
    }

    public Map<String, Object> reload(String modelName) {
        return restTemplate.postForObject(
                aiServiceUrl + "/models/{name}/reload", null, Map.class, modelName
        );
    }

    public List<ModelFile> browse(String directory) throws IOException {
        Path selected = resolve(directory == null ? "" : directory);
        if (!Files.isDirectory(selected)) {
            throw new IllegalArgumentException("目录不存在: " + directory);
        }
        try (var paths = Files.list(selected)) {
            return paths
                    .filter(path -> !path.getFileName().toString().equals("model-config.json"))
                    .map(this::toModelFile)
                    .sorted(Comparator.comparing(ModelFile::directory).reversed()
                            .thenComparing(ModelFile::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
    }

    public ModelFile upload(String directory, MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getOriginalFilename() == null) {
            throw new IllegalArgumentException("请选择要上传的模型文件");
        }
        Path targetDirectory = resolve(directory == null ? "" : directory);
        Files.createDirectories(targetDirectory);
        String safeName = Path.of(file.getOriginalFilename()).getFileName().toString();
        if (safeName.equals("model-config.json") || safeName.equals("model-config.tmp")) {
            throw new IllegalArgumentException("该文件名由系统保留");
        }
        Path target = targetDirectory.resolve(safeName).normalize();
        ensureInsideRoot(target);
        try (var input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return toModelFile(target);
    }

    private Path resolve(String relativePath) {
        Path root = Path.of(configuredRoot).toAbsolutePath().normalize();
        Path resolved = root.resolve(relativePath).normalize();
        ensureInsideRoot(resolved);
        return resolved;
    }

    private void ensureInsideRoot(Path path) {
        Path root = Path.of(configuredRoot).toAbsolutePath().normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("模型路径必须位于外部模型根目录内");
        }
        try {
            Path existing = path;
            while (existing != null && !Files.exists(existing)) {
                existing = existing.getParent();
            }
            if (existing != null && !existing.toRealPath().startsWith(root.toRealPath())) {
                throw new IllegalArgumentException("模型路径不能通过符号链接离开模型根目录");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法验证模型路径", exception);
        }
    }

    private ModelFile toModelFile(Path path) {
        try {
            Path root = Path.of(configuredRoot).toAbsolutePath().normalize();
            boolean directory = Files.isDirectory(path);
            return new ModelFile(
                    path.getFileName().toString(),
                    root.relativize(path.toAbsolutePath().normalize()).toString(),
                    directory,
                    directory ? null : Files.size(path)
            );
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取模型文件: " + path, exception);
        }
    }

    public record ModelFile(String name, String path, boolean directory, Long size) {
    }
}
