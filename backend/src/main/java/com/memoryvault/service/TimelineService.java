package com.memoryvault.service;

import com.memoryvault.dto.PhotoDTO;
import com.memoryvault.entity.Photo;
import com.memoryvault.repository.PhotoRepository;
import com.memoryvault.storage.MinioStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TimelineService {

    private final PhotoRepository photoRepository;
    private final MinioStorageService storageService;

    public Map<Integer, Map<Integer, List<PhotoDTO>>> getTimelineGrouped() {
        List<Photo> photos = photoRepository.findAll().stream()
                .filter(p -> p.getExifDate() != null)
                .sorted(Comparator.comparing(Photo::getExifDate).reversed())
                .toList();

        Map<Integer, Map<Integer, List<PhotoDTO>>> result = new TreeMap<>(Comparator.reverseOrder());

        for (Photo photo : photos) {
            int year = photo.getExifDate().getYear();
            int month = photo.getExifDate().getMonthValue();

            result.computeIfAbsent(year, k -> new TreeMap<>(Comparator.reverseOrder()))
                    .computeIfAbsent(month, k -> new ArrayList<>())
                    .add(toDTO(photo));
        }

        return result;
    }

    public List<PhotoDTO> getPhotosByDateRange(LocalDateTime start, LocalDateTime end) {
        return photoRepository.findByExifDateRange(start, end).stream()
                .map(this::toDTO)
                .toList();
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
        dto.setCreatedAt(photo.getCreatedAt());
        try {
            String thumbExt = photo.getOriginalFilename() != null
                    && photo.getOriginalFilename().toLowerCase().endsWith(".webp") ? "webp" : "jpg";
            dto.setThumbnailUrl(storageService.getThumbnailUrl(photo.getFileHashMd5() + "/thumb." + thumbExt));
        } catch (Exception e) {
            // ignore
        }
        return dto;
    }
}
