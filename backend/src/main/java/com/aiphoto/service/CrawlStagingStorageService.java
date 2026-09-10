package com.aiphoto.service;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CrawlStagingStorageService {

    @Value("${app.storage.crawl-dir:./data/crawl}")
    private String crawlDir;

    @PostConstruct
    void init() throws Exception {
        Files.createDirectories(basePath());
    }

    public String write(Long jobId, Long assetId, byte[] bytes, String extension) throws Exception {
        Path directory = basePath().resolve(jobId.toString()).normalize();
        Files.createDirectories(directory);
        Path target = directory.resolve(assetId + "." + extension).normalize();
        requireManaged(target);
        Path temporary = directory.resolve(assetId + ".part").normalize();
        Files.write(temporary, bytes);
        Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return basePath().relativize(target).toString();
    }

    public String writeThumbnail(Long jobId, Long assetId, byte[] bytes) throws Exception {
        Path directory = basePath().resolve(jobId.toString()).normalize();
        Files.createDirectories(directory);
        Path target = directory.resolve(assetId + ".thumb.jpg").normalize();
        requireManaged(target);
        Files.write(target, bytes);
        return basePath().relativize(target).toString();
    }

    public byte[] read(String relativePath) throws Exception {
        Path target = basePath().resolve(relativePath).normalize();
        requireManaged(target);
        return Files.readAllBytes(target);
    }

    public void delete(String relativePath) throws Exception {
        if (relativePath == null) return;
        Path target = basePath().resolve(relativePath).normalize();
        requireManaged(target);
        Files.deleteIfExists(target);
    }

    private Path basePath() {
        return Paths.get(crawlDir).toAbsolutePath().normalize();
    }

    private void requireManaged(Path path) {
        if (!path.startsWith(basePath())) {
            throw new IllegalArgumentException("非法采集文件路径");
        }
    }
}
