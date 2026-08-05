package com.memoryvault.storage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
public class LocalStorageService {

    @Value("${app.storage.photos-dir:./data/photos}")
    private String photosDir;

    @Value("${app.storage.thumbs-dir:./data/thumbs}")
    private String thumbsDir;

    private static final String PHOTO_BUCKET = "photos";
    private static final String THUMB_BUCKET = "thumbs";

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(photosDir));
            Files.createDirectories(Paths.get(thumbsDir));
            log.info("Local storage initialized: photos={}, thumbs={}", photosDir, thumbsDir);
        } catch (IOException e) {
            log.error("Failed to initialize local storage directories", e);
        }
    }

    public String uploadPhoto(byte[] data, String objectName, String contentType) throws Exception {
        Path target = Paths.get(photosDir, objectName);
        Files.createDirectories(target.getParent());
        Files.write(target, data);
        return objectName;
    }

    public String uploadThumbnail(byte[] data, String objectName) throws Exception {
        Path target = Paths.get(thumbsDir, objectName);
        Files.createDirectories(target.getParent());
        Files.write(target, data);
        return objectName;
    }

    public InputStream downloadPhoto(String objectName) throws Exception {
        Path source = Paths.get(photosDir, objectName);
        return Files.newInputStream(source);
    }

    public byte[] downloadBytes(String objectName) throws Exception {
        Path source = Paths.get(photosDir, objectName);
        return Files.readAllBytes(source);
    }

    /**
     * Generate a URL for browser access via nginx static file serving.
     * Returns a relative path that nginx serves from local filesystem.
     */
    public String getPresignedUrl(String bucket, String objectName) throws Exception {
        return "/media/" + bucket + "/" + objectName;
    }

    public String getPhotoUrl(String objectName) throws Exception {
        return getPresignedUrl(PHOTO_BUCKET, objectName);
    }

    public String getThumbnailUrl(String objectName) throws Exception {
        return getPresignedUrl(THUMB_BUCKET, objectName);
    }

    public void deleteObject(String objectName) throws Exception {
        Path target = Paths.get(photosDir, objectName);
        Files.deleteIfExists(target);
    }

    public void deleteObject(String bucket, String objectName) throws Exception {
        Path baseDir = PHOTO_BUCKET.equals(bucket) ? Paths.get(photosDir) : Paths.get(thumbsDir);
        Path target = baseDir.resolve(objectName);
        Files.deleteIfExists(target);
    }
}
