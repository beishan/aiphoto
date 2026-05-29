package com.memoryvault.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.memoryvault.async.PhotoIndexingConsumer;
import com.memoryvault.config.RabbitMQConfig;
import com.memoryvault.dto.PhotoDTO;
import com.memoryvault.entity.AiTask;
import com.memoryvault.entity.Photo;
import com.memoryvault.repository.AiTaskRepository;
import com.memoryvault.repository.PhotoRepository;
import com.memoryvault.storage.MinioStorageService;
import lombok.RequiredArgsConstructor;
import com.memoryvault.exception.DuplicateFileException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final MinioStorageService storageService;
    private final SettingService settingService;
    private final RabbitTemplate rabbitTemplate;
    private final AiTaskRepository aiTaskRepository;

    @Transactional
    public PhotoDTO uploadPhoto(MultipartFile file, Long userId) throws Exception {
        byte[] data = file.getBytes();
        String hashMd5 = computeMd5(data);
        String originalFilename = file.getOriginalFilename();

        // Check for duplicates
        if (photoRepository.findByFileHashMd5(hashMd5).isPresent()) {
            throw new DuplicateFileException("Duplicate file detected");
        }

        // Apply naming rule
        String namingRule = settingService.getSetting(userId, "photo_naming_rule");
        String renamedFilename = applyNamingRule(namingRule, originalFilename);

        // Upload original to MinIO
        String objectName = hashMd5 + "/" + renamedFilename;
        storageService.uploadPhoto(data, objectName, file.getContentType());

        // Generate thumbnail (skip if not a valid image)
        boolean webp = isWebP(data);
        String thumbName = webp ? hashMd5 + "/thumb.webp" : hashMd5 + "/thumb.jpg";
        try {
            byte[] thumbnail = generateThumbnail(data);
            storageService.uploadThumbnail(thumbnail, thumbName);
        } catch (Exception e) {
            log.warn("Failed to generate thumbnail for {}: {}", originalFilename, e.getMessage());
            thumbName = null;
        }

        // Parse EXIF metadata
        Photo photo = new Photo();
        photo.setFilePath(objectName);
        photo.setFileHashMd5(hashMd5);
        photo.setFileSize((long) data.length);
        photo.setMediaType(detectMediaType(originalFilename));
        photo.setOriginalFilename(originalFilename);

        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new java.io.ByteArrayInputStream(data));
            extractExifData(metadata, photo);
        } catch (Exception e) {
            log.warn("Failed to read EXIF data for {}: {}", originalFilename, e.getMessage());
        }

        photo = photoRepository.save(photo);

        // Trigger AI indexing
        AiTask aiTask = new AiTask();
        aiTask.setType(AiTask.TaskType.INDEX);
        aiTask.setPhotoIdsJson("[" + photo.getId() + "]");
        aiTask = aiTaskRepository.save(aiTask);

        PhotoIndexingConsumer.PhotoIndexMessage message = new PhotoIndexingConsumer.PhotoIndexMessage();
        message.setTaskId(aiTask.getId());
        message.setPhotoIds(List.of(photo.getId()));
        rabbitTemplate.convertAndSend(RabbitMQConfig.QUEUE_PHOTO_INDEX, message);

        return toDTO(photo);
    }

    public Page<PhotoDTO> listPhotos(Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "id"));
        return photoRepository.findAll(sorted).map(this::toDTO);
    }

    public PhotoDTO getPhoto(Long id) {
        Photo photo = photoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Photo not found"));
        return toDTO(photo);
    }

    @Transactional
    public PhotoDTO updatePhoto(Long id, PhotoDTO updates) {
        Photo photo = photoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Photo not found"));

        if (updates.getRating() != null) photo.setRating(updates.getRating());
        if (updates.getNote() != null) photo.setNote(updates.getNote());
        if (updates.getFavorite() != null) photo.setFavorite(updates.getFavorite());

        photo = photoRepository.save(photo);
        return toDTO(photo);
    }

    @Transactional
    public void deletePhoto(Long id) {
        photoRepository.deleteById(id);
    }

    public Page<PhotoDTO> getFavorites(Pageable pageable) {
        return photoRepository.findFavorites(pageable).map(this::toDTO);
    }

    public Page<PhotoDTO> getByRating(int minRating, Pageable pageable) {
        return photoRepository.findByMinRating(minRating, pageable).map(this::toDTO);
    }

    private void extractExifData(Metadata metadata, Photo photo) {
        // Extract date
        ExifSubIFDDirectory exifDir = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        if (exifDir != null) {
            Date date = exifDir.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
            if (date != null) {
                photo.setExifDate(date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
            }
        }

        // Extract GPS
        GpsDirectory gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory.class);
        if (gpsDir != null && gpsDir.getGeoLocation() != null) {
            photo.setGpsLat(gpsDir.getGeoLocation().getLatitude());
            photo.setGpsLng(gpsDir.getGeoLocation().getLongitude());
        }

        // Extract dimensions
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
                log.warn("Failed to extract image dimensions: {}", e.getMessage());
            }
        }
    }

    private byte[] generateThumbnail(byte[] imageData) throws Exception {
        // WebP not supported by Java ImageIO - use original as thumbnail
        if (isWebP(imageData)) {
            log.info("WebP detected, using original as thumbnail");
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

    private boolean isWebP(byte[] data) {
        // WebP magic bytes: "RIFF" + 4 bytes + "WEBP"
        return data.length >= 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
    }

    private boolean isWebPFilename(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".webp");
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

    private String applyNamingRule(String rule, String originalFilename) {
        if (rule == null || rule.isEmpty() || "original".equals(rule)) {
            return originalFilename;
        }

        String ext = "";
        String nameWithoutExt = originalFilename;
        int lastDot = originalFilename.lastIndexOf('.');
        if (lastDot > 0) {
            ext = originalFilename.substring(lastDot);
            nameWithoutExt = originalFilename.substring(0, lastDot);
        }

        LocalDateTime now = LocalDateTime.now();

        switch (rule) {
            case "date_original":
                return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "_" + nameWithoutExt + ext;
            case "date_time":
                return now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ext;
            case "uuid":
                return UUID.randomUUID().toString().substring(0, 8) + ext;
            case "timestamp":
                return String.valueOf(System.currentTimeMillis()) + ext;
            case "custom":
                // Custom prefix + sequence number based on timestamp
                return "photo_" + System.currentTimeMillis() + ext;
            default:
                return originalFilename;
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

    private PhotoDTO toDTO(Photo photo) {
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

        try {
            String thumbExt = isWebPFilename(photo.getOriginalFilename()) ? "webp" : "jpg";
            dto.setThumbnailUrl(storageService.getThumbnailUrl(photo.getFileHashMd5() + "/thumb." + thumbExt));
        } catch (Exception e) {
            log.warn("Failed to generate thumbnail URL for photo {}", photo.getId());
        }

        try {
            dto.setOriginalUrl(storageService.getPhotoUrl(photo.getFilePath()));
        } catch (Exception e) {
            log.warn("Failed to generate original URL for photo {}", photo.getId());
        }

        return dto;
    }
}
