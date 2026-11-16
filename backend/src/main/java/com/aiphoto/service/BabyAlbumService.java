package com.aiphoto.service;

import com.aiphoto.dto.PhotoDTO;
import com.aiphoto.entity.Album;
import com.aiphoto.entity.Photo;
import com.aiphoto.repository.AlbumRepository;
import com.aiphoto.repository.PhotoRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Age uses NAS EXIF local dates; no phone timezone conversion is applied. */
@Service
@RequiredArgsConstructor
public class BabyAlbumService {
  private final AlbumRepository albumRepository;
  private final PhotoRepository photoRepository;
  private final AlbumService albumService;

  @Transactional(readOnly = true)
  public Page<PhotoDTO> page(Long id, int page, int size) {
    Album album = requireBaby(id);
    return photoRepository.babyPhotos(id, PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100)))
        .map(photo -> toDTO(photo, album.getBirthDate()));
  }

  /** Retains the Web array-map contract. Android uses the paginated endpoint. */
  @Transactional(readOnly = true)
  public Map<String, List<PhotoDTO>> getBabyAlbumTimeline(Long id) {
    Album album = requireBaby(id);
    Map<String, List<PhotoDTO>> result = new LinkedHashMap<>();
    album.getPhotos().stream()
        .filter(photo -> photo.getDeletedAt() == null)
        .sorted(Comparator.comparing(Photo::getExifDate,
            Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Photo::getId))
        .map(photo -> toDTO(photo, album.getBirthDate()))
        .forEach(photo -> result.computeIfAbsent(photo.getAgeLabel(), key -> new java.util.ArrayList<>()).add(photo));
    return result;
  }

  private Album requireBaby(Long id) {
    Album album = albumRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "相册不存在"));
    if (album.getType() != Album.AlbumType.BABY || album.getBirthDate() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先配置宝宝相册及出生日期");
    }
    return album;
  }

  private PhotoDTO toDTO(Photo photo, LocalDate birth) {
    PhotoDTO dto = albumService.toPhotoDTO(photo);
    dto.setAgeLabel(formatAge(birth, photo.getExifDate() == null ? null : photo.getExifDate().toLocalDate()));
    return dto;
  }

  static String formatAge(LocalDate birth, LocalDate taken) {
    if (taken == null) return "拍摄日期未知";
    if (taken.isBefore(birth)) return "出生前";
    int months = (taken.getYear() - birth.getYear()) * 12 + taken.getMonthValue() - birth.getMonthValue();
    // Clamped month anniversaries correctly handle Jan 31 and leap-day births.
    if (birth.plusMonths(months).isAfter(taken)) months--;
    if (months == 0) return "第 " + ChronoUnit.DAYS.between(birth, taken) + " 天";
    if (months < 12) return months + " 个月";
    return months % 12 == 0 ? months / 12 + " 岁" : months / 12 + " 岁 " + months % 12 + " 个月";
  }
}
