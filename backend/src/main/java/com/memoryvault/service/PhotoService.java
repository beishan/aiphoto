package com.memoryvault.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.memoryvault.async.PhotoIndexingConsumer;
import com.memoryvault.config.RabbitMQConfig;
import com.memoryvault.dto.PhotoDTO;
import com.memoryvault.dto.PhotoDetailDTO;
import com.memoryvault.dto.PersonDTO;
import com.memoryvault.dto.TagDTO;
import com.memoryvault.entity.AiTask;
import com.memoryvault.entity.Photo;
import com.memoryvault.entity.FaceCluster;
import com.memoryvault.entity.Person;
import com.memoryvault.entity.PhotoTag;
import com.memoryvault.repository.AiTaskRepository;
import com.memoryvault.repository.AlbumRepository;
import com.memoryvault.repository.CategoryRepository;
import com.memoryvault.repository.FaceClusterRepository;
import com.memoryvault.repository.PersonRepository;
import com.memoryvault.repository.PhotoRepository;
import com.memoryvault.repository.PhotoTagRepository;
import com.memoryvault.repository.TagRepository;
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
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final MinioStorageService storageService;
    private final SettingService settingService;
    private final RabbitTemplate rabbitTemplate;
    private final AiTaskRepository aiTaskRepository;
    private final FaceClusterRepository faceClusterRepository;
    private final PersonRepository personRepository;
    private final AlbumRepository albumRepository;
    private final CategoryRepository categoryRepository;
    private final PhotoTagRepository photoTagRepository;
    private final TagRepository tagRepository;

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
        String thumbExt = isVideoFile(originalFilename) ? "jpg" : (isWebP(data) ? "webp" : "jpg");
        String thumbName = hashMd5 + "/thumb." + thumbExt;
        try {
            byte[] thumbnail = generateThumbnail(data, originalFilename);
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

    public PhotoDetailDTO getPhotoDetail(Long id) {
        Photo photo = photoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Photo not found"));

        PhotoDetailDTO dto = new PhotoDetailDTO();
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
        dto.setFileHashMd5(photo.getFileHashMd5());
        dto.setFileHashPhash(photo.getFileHashPhash());

        try {
            String thumbExt = isVideoFile(photo.getOriginalFilename()) ? "jpg" :
                              (isWebPFilename(photo.getOriginalFilename()) ? "webp" : "jpg");
            dto.setThumbnailUrl(storageService.getThumbnailUrl(photo.getFileHashMd5() + "/thumb." + thumbExt));
            dto.setOriginalUrl(storageService.getPhotoUrl(photo.getFilePath()));
        } catch (Exception e) {
            log.warn("Failed to generate URLs for photo {}", id);
        }

        // Load tags
        List<PhotoTag> photoTags = photoTagRepository.findByPhotoId(id);
        List<TagDTO> tagDTOs = photoTags.stream().map(pt -> {
            TagDTO td = new TagDTO();
            td.setId(pt.getTagId());
            td.setConfidence(pt.getConfidence());
            td.setSource(pt.getSource().name());
            tagRepository.findById(pt.getTagId()).ifPresent(tag -> {
                td.setName(tag.getName());
                td.setColor(tag.getColor());
                td.setType(tag.getType().name());
                td.setCategory(tag.getCategory());
            });
            return td;
        }).toList();
        dto.setTags(tagDTOs);

        // Load people
        List<FaceCluster> faces = faceClusterRepository.findByPhoto(photo);
        List<PersonDTO> peopleDTOs = faces.stream()
                .filter(f -> f.getPerson() != null)
                .map(FaceCluster::getPerson)
                .distinct()
                .map(p -> {
                    PersonDTO pd = new PersonDTO();
                    pd.setId(p.getId());
                    pd.setName(p.getName());
                    pd.setPhotoCount(p.getPhotoCount());
                    return pd;
                }).toList();
        dto.setPeople(peopleDTOs);

        return dto;
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
        Photo photo = photoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Photo not found"));
        // Clear cover_photo_id references from albums and categories
        albumRepository.clearCoverPhotoRefs(id);
        categoryRepository.clearCoverPhotoRefs(id);
        // Get face clusters and update person photo counts before deleting
        List<FaceCluster> faceClusters = faceClusterRepository.findByPhoto(photo);
        if (!faceClusters.isEmpty()) {
            List<Long> faceIds = faceClusters.stream().map(FaceCluster::getId).toList();
            personRepository.clearCoverFaceRefs(faceIds);
            // Update photo count for each affected person
            java.util.Set<Person> affectedPersons = faceClusters.stream()
                    .map(FaceCluster::getPerson)
                    .filter(p -> p != null)
                    .collect(java.util.stream.Collectors.toSet());
            for (Person person : affectedPersons) {
                person.setPhotoCount(Math.max(0, person.getPhotoCount() - 1));
            }
            personRepository.saveAll(affectedPersons);
        }
        // Delete files from MinIO storage
        try {
            storageService.deleteObject(photo.getFilePath());
            String thumbExt = isVideoFile(photo.getOriginalFilename()) ? "jpg" :
                              (photo.getOriginalFilename() != null
                              && photo.getOriginalFilename().toLowerCase().endsWith(".webp") ? "webp" : "jpg");
            storageService.deleteObject(photo.getFileHashMd5() + "/thumb." + thumbExt);
        } catch (Exception e) {
            log.warn("Failed to delete storage objects for photo {}: {}", id, e.getMessage());
        }
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

    private byte[] generateThumbnail(byte[] imageData, String filename) throws Exception {
        // Check if it's a video file
        if (filename != null && filename.toLowerCase().matches(".*\\.(mp4|avi|mov|mkv|webm)$")) {
            return generateVideoThumbnail(imageData);
        }

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
        // WebP magic bytes: "RIFF" + 4 bytes + "WEBP"
        return data.length >= 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
    }

    private boolean isWebPFilename(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".webp");
    }

    private boolean isVideoFile(String filename) {
        return filename != null && filename.toLowerCase().matches(".*\\.(mp4|avi|mov|mkv|webm)$");
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
            String thumbExt = isVideoFile(photo.getOriginalFilename()) ? "jpg" :
                              (isWebPFilename(photo.getOriginalFilename()) ? "webp" : "jpg");
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
