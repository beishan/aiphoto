package com.memoryvault.service;

import com.memoryvault.dto.PhotoDTO;
import com.memoryvault.entity.Album;
import com.memoryvault.entity.Photo;
import com.memoryvault.repository.AlbumRepository;
import com.memoryvault.repository.PhotoRepository;
import com.memoryvault.storage.MinioStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BabyAlbumService {

    private final AlbumRepository albumRepository;
    private final PhotoRepository photoRepository;
    private final MinioStorageService storageService;

    /**
     * Get baby album with age labels.
     * Returns photos grouped by age (e.g., "第 30 天", "2 个月").
     */
    public Map<String, List<PhotoDTO>> getBabyAlbumTimeline(Long albumId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new RuntimeException("Album not found"));

        if (album.getType() != Album.AlbumType.BABY || album.getBirthDate() == null) {
            throw new RuntimeException("Not a baby album or birth date not set");
        }

        LocalDate birthDate = album.getBirthDate();
        List<Photo> photos = album.getPhotos().stream()
                .filter(p -> p.getExifDate() != null)
                .sorted(Comparator.comparing(Photo::getExifDate))
                .toList();

        Map<String, List<PhotoDTO>> result = new LinkedHashMap<>();

        for (Photo photo : photos) {
            LocalDate photoDate = photo.getExifDate().toLocalDate();
            String ageLabel = formatAge(birthDate, photoDate);

            result.computeIfAbsent(ageLabel, k -> new ArrayList<>())
                    .add(toDTO(photo));
        }

        return result;
    }

    private String formatAge(LocalDate birthDate, LocalDate photoDate) {
        long daysBetween = ChronoUnit.DAYS.between(birthDate, photoDate);

        if (daysBetween < 0) return "出生前";

        if (daysBetween < 30) {
            return "第 " + daysBetween + " 天";
        }

        Period period = Period.between(birthDate, photoDate);
        int months = period.getYears() * 12 + period.getMonths();

        if (months < 12) {
            return months + " 个月";
        }

        int years = period.getYears();
        int remainingMonths = period.getMonths();
        if (remainingMonths == 0) {
            return years + " 岁";
        }
        return years + " 岁 " + remainingMonths + " 个月";
    }

    private PhotoDTO toDTO(Photo photo) {
        PhotoDTO dto = new PhotoDTO();
        dto.setId(photo.getId());
        dto.setFilePath(photo.getFilePath());
        dto.setExifDate(photo.getExifDate());
        dto.setRating(photo.getRating());
        dto.setNote(photo.getNote());
        dto.setAiCaption(photo.getAiCaption());
        dto.setMediaType(photo.getMediaType().name());
        dto.setFavorite(photo.getFavorite());
        try {
            dto.setThumbnailUrl(storageService.getThumbnailUrl(photo.getFileHashMd5() + "/thumb.webp"));
        } catch (Exception e) {
            // ignore
        }
        return dto;
    }
}
