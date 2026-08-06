package com.memoryvault.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.memoryvault.async.PhotoIndexingService;
import com.memoryvault.dto.PhotoDTO;
import com.memoryvault.dto.ScanFolderDTO;
import com.memoryvault.entity.AiTask;
import com.memoryvault.entity.Photo;
import com.memoryvault.entity.ScanFolder;
import com.memoryvault.repository.AiTaskRepository;
import com.memoryvault.repository.PhotoRepository;
import com.memoryvault.repository.ScanFolderRepository;
import com.memoryvault.storage.LocalStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class FolderService {

    private final ScanFolderRepository scanFolderRepository;
    private final PhotoRepository photoRepository;
    private final LocalStorageService storageService;
    private final AiTaskRepository aiTaskRepository;
    private final PhotoIndexingService photoIndexingService;

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".tiff", ".tif", ".webp", ".heic", ".heif"
    );

    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            ".mp4", ".avi", ".mov", ".mkv", ".webm"
    );

    @Transactional
    public ScanFolderDTO addFolder(ScanFolderDTO dto) {
        // Validate path exists
        Path path = Paths.get(dto.getPath());
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            throw new IllegalArgumentException("路径不存在或不是文件夹: " + dto.getPath());
        }

        // Check duplicate path
        if (scanFolderRepository.existsByPath(dto.getPath())) {
            throw new IllegalArgumentException("该路径已添加: " + dto.getPath());
        }

        ScanFolder folder = new ScanFolder();
        folder.setName(dto.getName());
        folder.setPath(dto.getPath());
        folder.setStorageMode(ScanFolder.StorageMode.valueOf(dto.getStorageMode()));
        folder = scanFolderRepository.save(folder);

        return toDTO(folder);
    }

    public List<ScanFolderDTO> listFolders() {
        return scanFolderRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    public ScanFolderDTO getFolder(Long id) {
        ScanFolder folder = scanFolderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("文件夹不存在"));
        return toDTO(folder);
    }

    @Transactional
    public ScanFolderDTO updateFolder(Long id, ScanFolderDTO dto) {
        ScanFolder folder = scanFolderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("文件夹不存在"));
        if (dto.getName() != null) folder.setName(dto.getName());
        if (dto.getPath() != null) folder.setPath(dto.getPath());
        if (dto.getStorageMode() != null) folder.setStorageMode(ScanFolder.StorageMode.valueOf(dto.getStorageMode()));
        folder = scanFolderRepository.save(folder);
        return toDTO(folder);
    }

    @Transactional
    public ScanFolderDTO toggleEnabled(Long id) {
        ScanFolder folder = scanFolderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("文件夹不存在"));
        folder.setEnabled(!folder.getEnabled());
        folder = scanFolderRepository.save(folder);
        return toDTO(folder);
    }

    @Transactional
    public ScanFolderDTO toggleHidden(Long id) {
        ScanFolder folder = scanFolderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("文件夹不存在"));
        folder.setHidden(!folder.getHidden());
        folder = scanFolderRepository.save(folder);
        return toDTO(folder);
    }

    @Async
    public void scanAllFolders() {
        List<ScanFolder> folders = scanFolderRepository.findByEnabledTrue();
        for (ScanFolder folder : folders) {
            try {
                scanFolder(folder.getId());
            } catch (Exception e) {
                log.error("Failed to scan folder {}: {}", folder.getName(), e.getMessage());
            }
        }
    }

    @Transactional
    public void deleteFolder(Long id) {
        ScanFolder folder = scanFolderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("文件夹不存在"));

        // If LINK mode, we should delete associated photos since they reference external files
        // If COPY mode, photos are in persistent local storage and can stay
        if (folder.getStorageMode() == ScanFolder.StorageMode.LINK) {
            List<Photo> photos = photoRepository.findBySourceFolderId(id, Pageable.unpaged()).getContent();
            photoRepository.deleteAll(photos);
        }

        scanFolderRepository.deleteById(id);
    }

    @Async
    public void scanFolder(Long folderId) {
        ScanFolder folder = scanFolderRepository.findById(folderId)
                .orElseThrow(() -> new RuntimeException("文件夹不存在"));

        if (folder.getScanStatus() == ScanFolder.ScanStatus.SCANNING) {
            log.warn("Folder {} is already being scanned", folderId);
            return;
        }

        // Update status to SCANNING
        folder.setScanStatus(ScanFolder.ScanStatus.SCANNING);
        folder.setErrorMessage(null);
        scanFolderRepository.save(folder);

        try {
            Path dirPath = Paths.get(folder.getPath());
            if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
                throw new IOException("路径不存在或不是文件夹: " + folder.getPath());
            }

            // Find all image files recursively
            List<Path> imageFiles = new ArrayList<>();
            try (Stream<Path> walk = Files.walk(dirPath)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> isImageFile(p.toString()))
                        .forEach(imageFiles::add);
            }

            log.info("Found {} media files in folder: {}", imageFiles.size(), folder.getPath());

            int imported = 0;
            int skipped = 0;

            for (Path imagePath : imageFiles) {
                try {
                    boolean success = importImage(imagePath, folder);
                    if (success) {
                        imported++;
                    } else {
                        skipped++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to import {}: {}", imagePath, e.getMessage());
                    skipped++;
                }
            }

            // Update folder status
            folder.setScanStatus(ScanFolder.ScanStatus.COMPLETED);
            folder.setLastScanAt(LocalDateTime.now());
            folder.setPhotoCount((int) photoRepository.countBySourceFolderId(folderId));
            scanFolderRepository.save(folder);

            log.info("Scan completed for folder {}: {} imported, {} skipped", folder.getName(), imported, skipped);

        } catch (Exception e) {
            log.error("Scan failed for folder {}: {}", folder.getName(), e.getMessage(), e);
            folder.setScanStatus(ScanFolder.ScanStatus.ERROR);
            folder.setErrorMessage(e.getMessage());
            scanFolderRepository.save(folder);
        }
    }

    private boolean importImage(Path imagePath, ScanFolder folder) throws Exception {
        byte[] data = Files.readAllBytes(imagePath);
        String hashMd5 = computeMd5(data);
        String filename = imagePath.getFileName().toString();

        // Check for duplicates
        if (photoRepository.findByFileHashMd5(hashMd5).isPresent()) {
            log.debug("Skipping duplicate: {}", filename);
            return false;
        }

        String contentType = probeContentType(imagePath);

        if (folder.getStorageMode() == ScanFolder.StorageMode.COPY) {
            // Copy to persistent local storage
            String objectName = hashMd5 + "/" + filename;
            storageService.uploadPhoto(data, objectName, contentType);

            // Generate thumbnail
            String thumbExt = isVideoFile(filename) ? "jpg" : (isWebP(data) ? "webp" : "jpg");
            String thumbName = hashMd5 + "/thumb." + thumbExt;
            try {
                byte[] thumbnail = generateThumbnail(data, filename);
                storageService.uploadThumbnail(thumbnail, thumbName);
            } catch (Exception e) {
                log.warn("Failed to generate thumbnail for {}: {}", filename, e.getMessage());
            }

            // Save photo entity
            Photo photo = createPhotoEntity(data, filename, objectName, hashMd5, folder.getId());
            photoRepository.save(photo);

            // Trigger AI indexing
            triggerAiIndexing(photo.getId());

        } else {
            // LINK mode - just record the path
            Photo photo = createPhotoEntity(data, filename, imagePath.toAbsolutePath().toString(), hashMd5, folder.getId());
            photoRepository.save(photo);

            // Trigger AI indexing
            triggerAiIndexing(photo.getId());
        }

        return true;
    }

    private Photo createPhotoEntity(byte[] data, String filename, String filePath, String hashMd5, Long folderId) throws Exception {
        Photo photo = new Photo();
        photo.setFilePath(filePath);
        photo.setFileHashMd5(hashMd5);
        photo.setFileSize((long) data.length);
        photo.setMediaType(detectMediaType(filename));
        photo.setOriginalFilename(filename);
        photo.setSourceFolderId(folderId);

        // Parse EXIF data
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new java.io.ByteArrayInputStream(data));
            extractExifData(metadata, photo);
        } catch (Exception e) {
            log.warn("Failed to read EXIF data for {}: {}", filename, e.getMessage());
        }

        return photo;
    }

    private void triggerAiIndexing(Long photoId) {
        try {
            AiTask aiTask = new AiTask();
            aiTask.setType(AiTask.TaskType.INDEX);
            aiTask.setPhotoIdsJson("[" + photoId + "]");
            aiTask = aiTaskRepository.save(aiTask);

            photoIndexingService.indexPhotos(aiTask.getId(), List.of(photoId));
        } catch (Exception e) {
            log.warn("Failed to trigger AI indexing for photo {}: {}", photoId, e.getMessage());
        }
    }

    private void extractExifData(Metadata metadata, Photo photo) {
        ExifSubIFDDirectory exifDir = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        if (exifDir != null) {
            Date date = exifDir.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
            if (date != null) {
                photo.setExifDate(date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
            }
        }

        GpsDirectory gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory.class);
        if (gpsDir != null && gpsDir.getGeoLocation() != null) {
            photo.setGpsLat(gpsDir.getGeoLocation().getLatitude());
            photo.setGpsLng(gpsDir.getGeoLocation().getLongitude());
        }

        ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
        if (ifd0 != null) {
            try {
                if (ifd0.containsTag(ExifIFD0Directory.TAG_IMAGE_WIDTH)) {
                    photo.setWidth(ifd0.getInt(ExifIFD0Directory.TAG_IMAGE_WIDTH));
                }
                if (ifd0.containsTag(ExifIFD0Directory.TAG_IMAGE_HEIGHT)) {
                    photo.setHeight(ifd0.getInt(ExifIFD0Directory.TAG_IMAGE_HEIGHT));
                }
            } catch (Exception e) {
                log.warn("Failed to extract dimensions: {}", e.getMessage());
            }
        }
    }

    private byte[] generateThumbnail(byte[] imageData, String filename) throws Exception {
        // Check if it's a video file
        if (isVideoFile(filename)) {
            return generateVideoThumbnail(imageData);
        }

        if (isWebP(imageData)) {
            return imageData;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Thumbnails.of(new java.io.ByteArrayInputStream(imageData))
                .size(400, 400)
                .outputFormat("jpg")
                .outputQuality(0.8)
                .toOutputStream(baos);
        return baos.toByteArray();
    }

    private byte[] generateVideoThumbnail(byte[] videoData) throws Exception {
        File tempVideo = null;
        File tempThumb = null;
        try {
            // Create temp files
            tempVideo = Files.createTempFile("video_", ".tmp").toFile();
            tempThumb = Files.createTempFile("thumb_", ".jpg").toFile();

            // Write video data to temp file
            try (FileOutputStream fos = new FileOutputStream(tempVideo)) {
                fos.write(videoData);
            }

            // Use FFmpeg to extract first frame as thumbnail
            ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-i", tempVideo.getAbsolutePath(),
                "-ss", "00:00:00",
                "-vframes", "1",
                "-vf", "scale=400:400:force_original_aspect_ratio=decrease,pad=400:400:(ow-iw)/2:(oh-ih)/2",
                "-y",
                tempThumb.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("FFmpeg process timed out");
            }

            if (process.exitValue() != 0) {
                throw new RuntimeException("FFmpeg exited with code " + process.exitValue());
            }

            return Files.readAllBytes(tempThumb.toPath());
        } finally {
            if (tempVideo != null) tempVideo.delete();
            if (tempThumb != null) tempThumb.delete();
        }
    }

    private boolean isWebP(byte[] data) {
        return data.length >= 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
    }

    private String computeMd5(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private boolean isImageFile(String filename) {
        String lower = filename.toLowerCase();
        return IMAGE_EXTENSIONS.stream().anyMatch(lower::endsWith)
                || VIDEO_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private boolean isVideoFile(String filename) {
        return filename != null && filename.toLowerCase().matches(".*\\.(mp4|avi|mov|mkv|webm)$");
    }

    private String probeContentType(Path path) {
        try {
            String contentType = Files.probeContentType(path);
            return contentType != null ? contentType : "application/octet-stream";
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }

    private Photo.MediaType detectMediaType(String filename) {
        if (filename == null) return Photo.MediaType.PHOTO;
        String lower = filename.toLowerCase();
        if (lower.endsWith(".gif")) return Photo.MediaType.GIF;
        if (lower.matches(".*\\.(mp4|avi|mov|mkv|webm)$")) return Photo.MediaType.VIDEO;
        if (lower.matches(".*\\.(cr2|nef|arw|dng|raw)$")) return Photo.MediaType.RAW;
        return Photo.MediaType.PHOTO;
    }

    public Page<PhotoDTO> getFolderPhotos(Long folderId, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "id"));
        return photoRepository.findBySourceFolderId(folderId, sorted).map(this::toPhotoDTO);
    }

    public List<Map<String, Object>> browseDirectories(String path) {
        List<Map<String, Object>> result = new ArrayList<>();

        Path dirPath;
        if (path == null || path.isEmpty()) {
            // List root directories
            dirPath = null;
        } else {
            dirPath = Paths.get(path);
        }

        try {
            if (dirPath == null) {
                // List root paths (drive letters on Windows, / on Linux)
                File[] roots = File.listRoots();
                if (roots != null) {
                    for (File root : roots) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("name", root.getPath());
                        item.put("path", root.getAbsolutePath());
                        item.put("isDirectory", true);
                        item.put("readable", root.canRead());
                        result.add(item);
                    }
                }
            } else {
                // List subdirectories of the given path
                if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
                    throw new IllegalArgumentException("路径不存在或不是文件夹: " + path);
                }

                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
                    for (Path entry : stream) {
                        if (Files.isDirectory(entry)) {
                            Map<String, Object> item = new HashMap<>();
                            item.put("name", entry.getFileName().toString());
                            item.put("path", entry.toAbsolutePath().toString());
                            item.put("isDirectory", true);
                            try {
                                item.put("readable", Files.isReadable(entry));
                            } catch (Exception e) {
                                item.put("readable", false);
                            }
                            result.add(item);
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to browse directory {}: {}", path, e.getMessage());
            throw new RuntimeException("无法读取目录: " + e.getMessage());
        }

        // Sort by name
        result.sort((a, b) -> ((String) a.get("name")).compareToIgnoreCase((String) b.get("name")));

        return result;
    }

    private ScanFolderDTO toDTO(ScanFolder folder) {
        ScanFolderDTO dto = new ScanFolderDTO();
        dto.setId(folder.getId());
        dto.setName(folder.getName());
        dto.setPath(folder.getPath());
        dto.setStorageMode(folder.getStorageMode().name());
        dto.setScanStatus(folder.getScanStatus().name());
        dto.setLastScanAt(folder.getLastScanAt());
        dto.setPhotoCount(folder.getPhotoCount());
        dto.setVideoCount(folder.getVideoCount());
        dto.setFileCount(folder.getFileCount());
        dto.setScanProgress(folder.getScanProgress());
        dto.setEnabled(folder.getEnabled());
        dto.setHidden(folder.getHidden());
        dto.setErrorMessage(folder.getErrorMessage());
        dto.setCreatedAt(folder.getCreatedAt());
        return dto;
    }

    private PhotoDTO toPhotoDTO(Photo photo) {
        PhotoDTO dto = new PhotoDTO();
        dto.setId(photo.getId());
        dto.setFilePath(photo.getFilePath());
        dto.setExifDate(photo.getExifDate());
        dto.setGpsLat(photo.getGpsLat());
        dto.setGpsLng(photo.getGpsLng());
        dto.setRating(photo.getRating());
        dto.setNote(photo.getNote());
        dto.setAiCaption(photo.getAiCaption());
        dto.setWidth(photo.getWidth());
        dto.setHeight(photo.getHeight());
        dto.setFileSize(photo.getFileSize());
        dto.setMediaType(photo.getMediaType().name());
        dto.setFavorite(photo.getFavorite());
        dto.setOriginalFilename(photo.getOriginalFilename());
        dto.setCreatedAt(photo.getCreatedAt());

        // For LINK mode, generate URL from original path
        if (photo.getFilePath() != null && !photo.getFilePath().contains("/")) {
            // LINK mode - file is on local filesystem
            try {
                dto.setOriginalUrl("/api/files/raw?path=" + java.net.URLEncoder.encode(photo.getFilePath(), "UTF-8"));
            } catch (Exception e) {
                log.warn("Failed to encode file path: {}", e.getMessage());
            }
        } else {
            // COPY mode - file is in persistent local storage
            try {
                String thumbExt = photo.getOriginalFilename() != null
                        && photo.getOriginalFilename().toLowerCase().endsWith(".webp") ? "webp" : "jpg";
                dto.setThumbnailUrl(storageService.getThumbnailUrl(photo.getFileHashMd5() + "/thumb." + thumbExt));
                dto.setOriginalUrl(storageService.getPhotoUrl(photo.getFilePath()));
            } catch (Exception e) {
                log.warn("Failed to generate URL for photo {}", photo.getId());
            }
        }

        return dto;
    }
}
