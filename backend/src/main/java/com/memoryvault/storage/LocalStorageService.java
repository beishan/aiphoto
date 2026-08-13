package com.memoryvault.storage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
public class LocalStorageService {

    @Value("${app.storage.photos-dir:./data/photos}")
    private String photosDir;

    @Value("${app.storage.thumbs-dir:./data/thumbs}")
    private String thumbsDir;

    private static final String PHOTO_BUCKET = "photos";
    private static final String THUMB_BUCKET = "thumbs";
    private static final Pattern MD5_DIRECTORY = Pattern.compile("^[0-9a-fA-F]{32}$");

    @PostConstruct
    public void init() {
        try {
            Path photosPath = Paths.get(photosDir).toAbsolutePath().normalize();
            Path thumbsPath = Paths.get(thumbsDir).toAbsolutePath().normalize();
            Files.createDirectories(photosPath);
            Files.createDirectories(thumbsPath);
            int migratedPhotos = migrateLegacyHashDirectories(photosPath);
            int migratedThumbs = migrateLegacyHashDirectories(thumbsPath);
            log.info("Local storage initialized: photos={}, thumbs={}", photosDir, thumbsDir);
            if (migratedPhotos > 0 || migratedThumbs > 0) {
                log.info(
                    "Migrated legacy storage directories to two-level MD5 shards: photos={}, thumbs={}",
                    migratedPhotos,
                    migratedThumbs
                );
            }
        } catch (IOException e) {
            log.error("Failed to initialize local storage directories", e);
        }
    }

    public String uploadPhoto(byte[] data, String objectName, String contentType) throws Exception {
        Path target = resolveForWrite(photoBasePath(), objectName);
        Files.createDirectories(target.getParent());
        Files.write(target, data);
        return objectName;
    }

    public String uploadThumbnail(byte[] data, String objectName) throws Exception {
        Path target = resolveForWrite(thumbBasePath(), objectName);
        Files.createDirectories(target.getParent());
        Files.write(target, data);
        return objectName;
    }

    public InputStream downloadPhoto(String objectName) throws Exception {
        Path source = resolveForRead(photoBasePath(), objectName);
        return Files.newInputStream(source);
    }

    public byte[] downloadBytes(String objectName) throws Exception {
        Path source = resolveForRead(photoBasePath(), objectName);
        return Files.readAllBytes(source);
    }

    /**
     * Generate a URL for browser access via nginx static file serving.
     * Returns a relative path that nginx serves from local filesystem.
     */
    public String getPresignedUrl(String bucket, String objectName) throws Exception {
        Path baseDir = PHOTO_BUCKET.equals(bucket) ? photoBasePath() : thumbBasePath();
        Path physicalPath = resolveForRead(baseDir, objectName);
        String publicObjectName = baseDir.relativize(physicalPath).toString().replace('\\', '/');
        return "/media/" + bucket + "/" + publicObjectName;
    }

    public String getPhotoUrl(String objectName) throws Exception {
        return getPresignedUrl(PHOTO_BUCKET, objectName);
    }

    public String getThumbnailUrl(String objectName) throws Exception {
        return getPresignedUrl(THUMB_BUCKET, objectName);
    }

    public void deleteObject(String objectName) throws Exception {
        deleteManagedObject(photoBasePath(), objectName);
    }

    public void deleteObject(String bucket, String objectName) throws Exception {
        Path baseDir = PHOTO_BUCKET.equals(bucket) ? photoBasePath() : thumbBasePath();
        deleteManagedObject(baseDir, objectName);
    }

    static String shardObjectName(String objectName) {
        String normalized = objectName.replace('\\', '/');
        int separator = normalized.indexOf('/');
        String firstSegment = separator < 0 ? normalized : normalized.substring(0, separator);
        if (!MD5_DIRECTORY.matcher(firstSegment).matches()) {
            return normalized;
        }
        return firstSegment.substring(0, 2)
            + "/" + firstSegment.substring(2, 4)
            + "/" + normalized;
    }

    private Path photoBasePath() {
        return Paths.get(photosDir).toAbsolutePath().normalize();
    }

    private Path thumbBasePath() {
        return Paths.get(thumbsDir).toAbsolutePath().normalize();
    }

    private Path resolveForWrite(Path baseDir, String objectName) {
        return baseDir.resolve(shardObjectName(objectName)).normalize();
    }

    private Path resolveForRead(Path baseDir, String objectName) {
        Path sharded = resolveForWrite(baseDir, objectName);
        if (Files.exists(sharded)) {
            return sharded;
        }
        Path legacy = baseDir.resolve(objectName).normalize();
        return Files.exists(legacy) ? legacy : sharded;
    }

    private void deleteManagedObject(Path baseDir, String objectName) throws IOException {
        Path sharded = resolveForWrite(baseDir, objectName);
        Path legacy = baseDir.resolve(objectName).normalize();
        Files.deleteIfExists(sharded);
        if (!legacy.equals(sharded)) {
            Files.deleteIfExists(legacy);
        }
        deleteEmptyParents(baseDir, sharded.getParent());
        deleteEmptyParents(baseDir, legacy.getParent());
    }

    private void deleteEmptyParents(Path baseDir, Path directory) throws IOException {
        Path current = directory;
        while (current != null && current.startsWith(baseDir) && !current.equals(baseDir)) {
            if (Files.notExists(current)) {
                current = current.getParent();
                continue;
            }
            try (Stream<Path> children = Files.list(current)) {
                if (children.findAny().isPresent()) {
                    return;
                }
            }
            Files.deleteIfExists(current);
            current = current.getParent();
        }
    }

    private int migrateLegacyHashDirectories(Path baseDir) throws IOException {
        List<Path> legacyDirectories;
        try (Stream<Path> children = Files.list(baseDir)) {
            legacyDirectories = children
                .filter(Files::isDirectory)
                .filter(path -> MD5_DIRECTORY.matcher(path.getFileName().toString()).matches())
                .toList();
        }

        int migrated = 0;
        for (Path source : legacyDirectories) {
            String hash = source.getFileName().toString();
            Path target = baseDir
                .resolve(hash.substring(0, 2))
                .resolve(hash.substring(2, 4))
                .resolve(hash);
            Files.createDirectories(target.getParent());
            if (Files.notExists(target)) {
                try {
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(source, target);
                }
            } else {
                mergeDirectory(source, target);
            }
            if (Files.notExists(source)) {
                migrated++;
            }
        }
        return migrated;
    }

    private void mergeDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        List<Path> children;
        try (Stream<Path> stream = Files.list(source)) {
            children = stream.toList();
        }
        for (Path child : children) {
            Path targetChild = target.resolve(child.getFileName().toString());
            if (Files.isDirectory(child)) {
                mergeDirectory(child, targetChild);
            } else if (Files.notExists(targetChild)) {
                Files.move(child, targetChild);
            } else {
                log.warn("Storage migration kept conflicting legacy file: {}", child);
            }
        }
        try (Stream<Path> remaining = Files.list(source)) {
            if (remaining.findAny().isEmpty()) {
                Files.deleteIfExists(source);
            }
        }
    }
}
