package com.aiphoto.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aiphoto.entity.Album;
import com.aiphoto.entity.Photo;
import com.aiphoto.repository.AlbumRepository;
import com.aiphoto.repository.PhotoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class MobileAlbumPaginationTest {
    @Mock AlbumRepository albums;
    @Mock PhotoRepository photos;
    @InjectMocks AlbumService service;

    @Test void paginationIsBoundedAndMissingAlbumIs404() {
        when(albums.existsById(1L)).thenReturn(true);
        when(photos.albumPhotos(eq(1L), any())).thenAnswer(invocation -> {
            Pageable page = invocation.getArgument(1);
            assertEquals(0, page.getPageNumber());
            assertEquals(100, page.getPageSize());
            return new PageImpl<Photo>(List.of());
        });
        assertTrue(service.getAlbumPhotoPage(1L, -1, 9999).isEmpty());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.getAlbumPhotoPage(2L, 0, 40));
    }
    @Test void addingExistingPhotoDoesNotDuplicateAssociation() {
        var album = new Album(); var photo = new Photo(); photo.setId(7L);
        album.getPhotos().add(photo); album.setCoverPhoto(photo);
        when(albums.findById(1L)).thenReturn(Optional.of(album));
        when(photos.findById(7L)).thenReturn(Optional.of(photo));
        service.addPhotoToAlbum(1L, 7L);
        assertEquals(1, album.getPhotos().size());
    }
}
