package com.memoryvault.service;

import com.memoryvault.dto.PhotoDTO;
import com.memoryvault.entity.Photo;
import com.memoryvault.repository.PhotoRepository;
import com.memoryvault.storage.LocalStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimelineService {

    private final PhotoRepository photoRepository;
    private final LocalStorageService storageService;

    public Map<Integer, Map<Integer, List<PhotoDTO>>> getTimelineGrouped() {
        // Only show photos that are added to timeline
        List<Photo> photos = photoRepository.findTimelinePhotos();

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
                .filter(p -> p.getInTimeline() != null && p.getInTimeline())
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
        dto.setInTimeline(photo.getInTimeline());
        dto.setOriginalFilename(photo.getOriginalFilename());
        dto.setCreatedAt(photo.getCreatedAt());
        dto.setSourceFolderId(photo.getSourceFolderId());
        try {
            String thumbExt = photo.getOriginalFilename() != null
                    && photo.getOriginalFilename().toLowerCase().endsWith(".webp") ? "webp" : "jpg";
            dto.setThumbnailUrl(storageService.getThumbnailUrl(photo.getFileHashMd5() + "/thumb." + thumbExt));
            dto.setOriginalUrl(storageService.getPhotoUrl(photo.getFilePath()));
        } catch (Exception e) {
            // ignore
        }
        return dto;
    }
}
