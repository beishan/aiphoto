package com.memoryvault.service;

import com.memoryvault.async.TrainingTaskService;
import com.memoryvault.dto.AlbumDTO;
import com.memoryvault.dto.PhotoDTO;
import com.memoryvault.entity.*;
import com.memoryvault.repository.*;
import com.memoryvault.storage.LocalStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final PhotoRepository photoRepository;
    private final UserRepository userRepository;
    private final TrainingTaskService trainingTaskService;
    private final AiTaskRepository aiTaskRepository;
    private final LocalStorageService storageService;

    public List<AlbumDTO> listAlbums() {
        return albumRepository.findAll().stream().map(this::toDTO).toList();
    }

    public AlbumDTO getAlbum(Long id) {
        Album album = albumRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Album not found"));
        return toDTO(album);
    }

    @Transactional
    public AlbumDTO createAlbum(AlbumDTO albumDTO) {
        Album album = new Album();
        album.setName(albumDTO.getName());
        album.setDescription(albumDTO.getDescription());
        album.setType(Album.AlbumType.valueOf(albumDTO.getType()));
        album.setShared(albumDTO.getShared() != null ? albumDTO.getShared() : false);
        album.setBirthDate(albumDTO.getBirthDate());
        album = albumRepository.save(album);
        return toDTO(album);
    }

    @Transactional
    public AlbumDTO updateAlbum(Long id, AlbumDTO albumDTO) {
        Album album = albumRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Album not found"));
        album.setName(albumDTO.getName());
        album.setDescription(albumDTO.getDescription());
        album.setShared(albumDTO.getShared());
        album = albumRepository.save(album);
        return toDTO(album);
    }

    @Transactional
    public AlbumDTO setCoverPhoto(Long albumId, Long photoId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new RuntimeException("Album not found"));
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new RuntimeException("Photo not found"));
        album.setCoverPhoto(photo);
        album = albumRepository.save(album);
        return toDTO(album);
    }

    public List<PhotoDTO> getAlbumPhotos(Long albumId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new RuntimeException("Album not found"));
        return album.getPhotos().stream().map(this::toPhotoDTO).toList();
    }

    @Transactional
    public void addPhotoToAlbum(Long albumId, Long photoId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new RuntimeException("Album not found"));
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new RuntimeException("Photo not found"));
        album.getPhotos().add(photo);
        // 自动设置封面：如果相册没有封面，将第一张添加的照片设为封面
        if (album.getCoverPhoto() == null) {
            album.setCoverPhoto(photo);
        }
        albumRepository.save(album);
    }

    @Transactional
    public void removePhotoFromAlbum(Long albumId, Long photoId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new RuntimeException("Album not found"));
        album.getPhotos().removeIf(p -> p.getId().equals(photoId));
        albumRepository.save(album);
    }

    @Transactional
    public void deleteAlbum(Long id) {
        albumRepository.deleteById(id);
    }

    public Map<String, Object> trainAlbum(Long albumId, Double threshold) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new RuntimeException("Album not found"));

        AiTask aiTask = new AiTask();
        aiTask.setType(AiTask.TaskType.TRAIN);
        aiTask = aiTaskRepository.save(aiTask);

        trainingTaskService.trainAlbum(aiTask.getId(), albumId, threshold);

        return Map.of("taskId", aiTask.getId(), "message", "Training task created");
    }

    private AlbumDTO toDTO(Album album) {
        AlbumDTO dto = new AlbumDTO();
        dto.setId(album.getId());
        dto.setName(album.getName());
        dto.setDescription(album.getDescription());
        dto.setType(album.getType().name());
        dto.setShared(album.getShared());
        dto.setBirthDate(album.getBirthDate());
        dto.setPhotoCount(album.getPhotos().size());
        if (album.getCoverPhoto() != null) {
            dto.setCoverPhotoId(album.getCoverPhoto().getId());
            try {
                dto.setCoverPhotoUrl(storageService.getPhotoUrl(album.getCoverPhoto().getFilePath()));
            } catch (Exception e) {
                log.error("Failed to get cover photo URL", e);
            }
        }
        dto.setCreatedAt(album.getCreatedAt());
        return dto;
    }

    private PhotoDTO toPhotoDTO(Photo photo) {
        PhotoDTO dto = new PhotoDTO();
        dto.setId(photo.getId());
        dto.setFilePath(photo.getFilePath());
        try {
            String thumbExt = isWebPFilename(photo.getOriginalFilename()) ? "webp" : "jpg";
            dto.setThumbnailUrl(storageService.getThumbnailUrl(photo.getFileHashMd5() + "/thumb." + thumbExt));
            dto.setOriginalUrl(storageService.getPhotoUrl(photo.getFilePath()));
        } catch (Exception e) {
            log.error("Failed to get photo URL", e);
        }
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
        return dto;
    }

    private boolean isWebPFilename(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".webp");
    }
}
