package com.aiphoto.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aiphoto.dto.AlbumDTO;
import com.aiphoto.entity.Album;
import com.aiphoto.repository.*;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BabyAlbumMetadataTest {
  @Mock AlbumRepository albums;
  @Mock PhotoRepository photos;
  @InjectMocks AlbumService service;

  @Test void birthdayUpdateKeepsAlbumIdentityAndUnchangedFields() {
    var album = new Album(); album.setId(1L); album.setType(Album.AlbumType.BABY);
    album.setName("成长"); album.setDescription("我的宝宝"); album.setShared(true);
    album.setBirthDate(LocalDate.of(2020, 2, 29));
    when(albums.findById(1L)).thenReturn(Optional.of(album));
    when(albums.save(album)).thenReturn(album);
    var change = new AlbumDTO(); change.setBirthDate(LocalDate.of(2021, 3, 1));
    var result = service.updateAlbum(1L, change);
    assertEquals("成长", result.getName()); assertTrue(result.getShared());
    assertEquals("我的宝宝", result.getDescription()); assertEquals(LocalDate.of(2021, 3, 1), result.getBirthDate());
  }
  @Test void babyRequiresBirthDateAndRejectsFutureDateBeforeSaving() {
    var request = new AlbumDTO(); request.setName("宝宝"); request.setType("BABY");
    assertThrows(IllegalArgumentException.class, () -> service.createAlbum(request));
    request.setBirthDate(LocalDate.now().plusDays(1));
    assertThrows(IllegalArgumentException.class, () -> service.createAlbum(request));
    verify(albums, never()).save(any());
  }
}
