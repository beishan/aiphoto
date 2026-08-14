package com.aiphoto.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.aiphoto.repository.PhotoRepository;
import com.aiphoto.repository.ScanFolderRepository;
import com.aiphoto.repository.TagRepository;
import com.aiphoto.repository.PersonRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemInfoService {

    private final PhotoRepository photoRepository;
    private final ScanFolderRepository scanFolderRepository;
    private final TagRepository tagRepository;
    private final PersonRepository personRepository;
    private final RestTemplate restTemplate;

    @Value("${app.storage.photos-dir:./data/photos}")
    private String photosDir;

    @Value("${app.storage.thumbs-dir:./data/thumbs}")
    private String thumbsDir;

    @Value("${app.ai-model-root:/models}")
    private String modelRoot;

    @Value("${app.ai-service-url:http://localhost:8000}")
    private String aiServiceUrl;

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${app.version:0.2.1}")
    private String appVersion;

    /**
     * Get storage usage information.
     */
    public Map<String, Object> getStorageInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        
        info.put("photosDir", photosDir);
        info.put("thumbsDir", thumbsDir);
        info.put("modelDir", modelRoot);
        
        info.put("photosSize", getDirectorySize(photosDir));
        info.put("thumbsSize", getDirectorySize(thumbsDir));
        info.put("modelsSize", getDirectorySize(modelRoot));
        
        info.put("photosCount", photoRepository.count());
        info.put("totalStorageSize", (long) info.get("photosSize") + (long) info.get("thumbsSize") + (long) info.get("modelsSize"));
        
        // Disk free space
        try {
            Path dataPath = Paths.get(photosDir).toAbsolutePath().getParent();
            if (dataPath != null && Files.exists(dataPath)) {
                info.put("diskFreeSpace", dataPath.toFile().getFreeSpace());
                info.put("diskTotalSpace", dataPath.toFile().getTotalSpace());
            }
        } catch (Exception e) {
            log.warn("Failed to get disk space info", e);
        }
        
        return info;
    }

    /**
     * Get system information.
     */
    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        
        // Application info
        info.put("appName", "aiphoto");
        info.put("appVersion", appVersion);
        
        // Runtime info
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("javaVendor", System.getProperty("java.vendor"));
        info.put("osName", System.getProperty("os.name"));
        info.put("osVersion", System.getProperty("os.version"));
        info.put("osArch", System.getProperty("os.arch"));
        
        // Memory info
        Runtime runtime = Runtime.getRuntime();
        info.put("jvmMaxMemory", runtime.maxMemory());
        info.put("jvmTotalMemory", runtime.totalMemory());
        info.put("jvmFreeMemory", runtime.freeMemory());
        info.put("jvmUsedMemory", runtime.totalMemory() - runtime.freeMemory());
        info.put("availableProcessors", runtime.availableProcessors());
        
        // Database info
        info.put("databaseUrl", dbUrl);
        info.put("databaseType", "PostgreSQL 16 + pgvector");
        
        // Data statistics
        info.put("photoCount", photoRepository.count());
        info.put("folderCount", scanFolderRepository.count());
        info.put("tagCount", tagRepository.count());
        info.put("personCount", personRepository.count());
        
        // AI service status
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> health = restTemplate.getForObject(aiServiceUrl + "/health", Map.class);
            info.put("aiServiceStatus", health != null ? health.get("status") : "unknown");
            info.put("aiServiceUrl", aiServiceUrl);
            if (health != null) {
                info.put("aiModels", health.get("models"));
            }
        } catch (Exception e) {
            info.put("aiServiceStatus", "offline");
            info.put("aiServiceError", e.getMessage());
        }
        
        return info;
    }

    private long getDirectorySize(String dirPath) {
        Path path = Paths.get(dirPath);
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            return walk.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try { return Files.size(p); }
                        catch (IOException e) { return 0; }
                    })
                    .sum();
        } catch (IOException e) {
            log.warn("Failed to calculate directory size: {}", dirPath, e);
            return 0;
        }
    }
}
