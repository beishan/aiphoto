package com.aiphoto.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aiphoto.dto.PhotoDTO;
import com.aiphoto.entity.Album;
import com.aiphoto.entity.Photo;
import com.aiphoto.repository.AlbumRepository;
import com.aiphoto.repository.PhotoRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

class BabyAlbumServiceTest {
  @Test
  void ageUsesCalendarAnniversariesIncludingLeapDayAndMonthEnd() {
    LocalDate birth = LocalDate.of(2024, 1, 31);
    assertEquals("出生前", BabyAlbumService.formatAge(birth, birth.minusDays(1)));
    assertEquals("第 0 天", BabyAlbumService.formatAge(birth, birth));
    assertEquals("第 28 天", BabyAlbumService.formatAge(birth, LocalDate.of(2024, 2, 28)));
    assertEquals("1 个月", BabyAlbumService.formatAge(birth, LocalDate.of(2024, 2, 29)));
    assertEquals("第 30 天", BabyAlbumService.formatAge(LocalDate.of(2024, 5, 12), LocalDate.of(2024, 6, 11)));
    assertEquals("1 岁", BabyAlbumService.formatAge(LocalDate.of(2024, 2, 29), LocalDate.of(2025, 2, 28)));
    assertEquals("1 岁 1 个月", BabyAlbumService.formatAge(birth, LocalDate.of(2025, 2, 28)));
    assertEquals("拍摄日期未知", BabyAlbumService.formatAge(birth, null));
  }

  @Test
  void pageDoesNotMaterializeAlbumCollectionAndBoundsPageSize() {
    var albums = mock(AlbumRepository.class);
    var photos = mock(PhotoRepository.class);
    var mapper = mock(AlbumService.class);
    var album = new Album(); album.setType(Album.AlbumType.BABY); album.setBirthDate(LocalDate.of(2024, 1, 31));
    when(albums.findById(1L)).thenReturn(Optional.of(album));
    var photo = new Photo(); photo.setId(7L); photo.setExifDate(LocalDateTime.of(2024, 2, 29, 23, 59));
    var dto = new PhotoDTO(); dto.setId(7L); dto.setOriginalUrl("/media/photos/a.jpg");
    when(mapper.toPhotoDTO(photo)).thenReturn(dto);
    when(photos.babyPhotos(eq(1L), any())).thenAnswer(call -> {
      Pageable page = call.getArgument(1); assertEquals(0, page.getPageNumber()); assertEquals(100, page.getPageSize());
      return new PageImpl<>(List.of(photo));
    });
    var result = new BabyAlbumService(albums, photos, mapper).page(1L, -3, 999);
    assertEquals("1 个月", result.getContent().getFirst().getAgeLabel());
    assertEquals("/media/photos/a.jpg", result.getContent().getFirst().getOriginalUrl());
    verify(mapper).toPhotoDTO(photo);
  }

  @Test
  void legacyWebTimelineExcludesTrashAndRetainsUnknownDates() {
    var albums = mock(AlbumRepository.class); var mapper = mock(AlbumService.class);
    var album = new Album(); album.setType(Album.AlbumType.BABY); album.setBirthDate(LocalDate.of(2020, 1, 1));
    var missing = new Photo(); missing.setId(2L);
    var taken = new Photo(); taken.setId(1L); taken.setExifDate(LocalDateTime.of(2020, 1, 1, 0, 0));
    var trash = new Photo(); trash.setId(3L); trash.setDeletedAt(LocalDateTime.now());
    album.getPhotos().addAll(List.of(missing, trash, taken));
    when(albums.findById(1L)).thenReturn(Optional.of(album));
    when(mapper.toPhotoDTO(any())).thenAnswer(call -> { var dto = new PhotoDTO(); dto.setId(((Photo) call.getArgument(0)).getId()); return dto; });
    var result = new BabyAlbumService(albums, mock(PhotoRepository.class), mapper).getBabyAlbumTimeline(1L);
    assertEquals(List.of("第 0 天", "拍摄日期未知"), List.copyOf(result.keySet()));
    verify(mapper, never()).toPhotoDTO(trash);
  }

  @Test
  void wrongTypeAndMissingAlbumHaveExplicitStatus() {
    var albums = mock(AlbumRepository.class); var service = new BabyAlbumService(albums, mock(PhotoRepository.class), mock(AlbumService.class));
    when(albums.findById(1L)).thenReturn(Optional.empty());
    assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.page(1L, 0, 40)).getStatusCode().value());
    when(albums.findById(1L)).thenReturn(Optional.of(new Album()));
    assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.page(1L, 0, 40)).getStatusCode().value());
  }
}
