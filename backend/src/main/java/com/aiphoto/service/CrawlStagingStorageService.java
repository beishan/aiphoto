package com.aiphoto.service;

import com.aiphoto.storage.StorageRoots;
import com.aiphoto.storage.StorageRoots.Kind;
import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
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
        try (var storageLease = StorageRoots.writing(Kind.CRAWL)) {
            Path directory = basePath().resolve(jobId.toString()).normalize();
            Files.createDirectories(directory);
            Path target = directory.resolve(assetId + "." + extension).normalize();
            requireManaged(target, basePath());
            Path temporary = directory.resolve(assetId + ".part").normalize();
            Files.write(temporary, bytes);
            Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return basePath().relativize(target).toString();
        }
    }

    public String writeThumbnail(Long jobId, Long assetId, byte[] bytes) throws Exception {
        try (var storageLease = StorageRoots.writing(Kind.CRAWL)) {
            Path directory = basePath().resolve(jobId.toString()).normalize();
            Files.createDirectories(directory);
            Path target = directory.resolve(assetId + ".thumb.jpg").normalize();
            requireManaged(target, basePath());
            Files.write(target, bytes);
            return basePath().relativize(target).toString();
        }
    }

    public String writeSkipThumbnail(Long skipId, byte[] bytes) throws Exception {
        try (var storageLease = StorageRoots.writing(Kind.CRAWL)) {
            Path directory = basePath().resolve("skip-thumbnails");
            Files.createDirectories(directory);
            Path target = directory.resolve(skipId + ".jpg");
            Files.write(target, bytes);
            return basePath().relativize(target).toString();
        }
    }

    public byte[] read(String relativePath) throws Exception {
        Path base = basePath();
        Path target = base.resolve(relativePath).normalize();
        requireManaged(target, base);
        return Files.readAllBytes(target);
    }

    public void delete(String relativePath) throws Exception {
        try (var storageLease = StorageRoots.writing(Kind.CRAWL)) {
            if (relativePath == null) return;
            Path base = basePath();
            Path target = base.resolve(relativePath).normalize();
            requireManaged(target, base);
            Files.deleteIfExists(target);
        }
    }

    private Path basePath() {
        return StorageRoots.active(crawlDir, Kind.CRAWL);
    }

    private void requireManaged(Path path, Path base) {
        if (!path.startsWith(base)) {
            throw new IllegalArgumentException("非法采集文件路径");
        }
    }
}
