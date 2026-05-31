package com.memoryvault.controller;

import com.memoryvault.dto.PhotoDTO;
import com.memoryvault.entity.Photo;
import com.memoryvault.service.DedupService;
import com.memoryvault.storage.MinioStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/dedup")
@RequiredArgsConstructor
public class DedupController {

    private final DedupService dedupService;
    private final MinioStorageService storageService;

    @GetMapping("/groups")
    public ResponseEntity<List<List<PhotoDTO>>> getDuplicateGroups() {
        return ResponseEntity.ok(convertGroups(dedupService.findExactDuplicates()));
    }

    @GetMapping("/similar")
    public ResponseEntity<List<List<PhotoDTO>>> getSimilarGroups() {
        return ResponseEntity.ok(convertGroups(dedupService.findSimilarPhotos()));
    }

    @DeleteMapping("/{photoId}")
    public ResponseEntity<Void> deletePhoto(@PathVariable Long photoId) {
        dedupService.deletePhoto(photoId);
        return ResponseEntity.noContent().build();
    }

    private List<List<PhotoDTO>> convertGroups(List<List<Photo>> groups) {
        List<List<PhotoDTO>> result = new ArrayList<>();
        for (List<Photo> group : groups) {
            List<PhotoDTO> dtoGroup = new ArrayList<>();
            for (Photo photo : group) {
                dtoGroup.add(toPhotoDTO(photo));
            }
            result.add(dtoGroup);
        }
        return result;
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
        try {
            String thumbExt = photo.getOriginalFilename() != null
                    && photo.getOriginalFilename().toLowerCase().endsWith(".webp") ? "webp" : "jpg";
            dto.setThumbnailUrl(storageService.getThumbnailUrl(
                    photo.getFileHashMd5() + "/thumb." + thumbExt));
            dto.setOriginalUrl(storageService.getPhotoUrl(photo.getFilePath()));
        } catch (Exception e) {
            log.warn("Failed to resolve URLs for photo {}", photo.getId());
        }
        return dto;
    }
}
