package com.aiphoto.async;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.aiphoto.entity.Album;
import com.aiphoto.entity.Photo;
import com.aiphoto.repository.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TrainingResultServiceTest {
    @Test
    void addsEveryMatchWithoutDuplicatingSamples() {
        AlbumRepository albums = mock(AlbumRepository.class);
        PhotoRepository photos = mock(PhotoRepository.class);
        TrainingSetRepository training = mock(TrainingSetRepository.class);
        Album album = new Album();
        album.setId(10L);
        Photo sample = photo(1L);
        Photo second = photo(2L);
        Photo third = photo(3L);
        album.getPhotos().add(sample);
        when(albums.findForTraining(10L)).thenReturn(Optional.of(album));
        when(training.findByAlbumId(10L)).thenReturn(Optional.empty());
        when(photos.findAllByVectorSimilarity(anyString(), eq(0.25)))
                .thenReturn(List.of(sample, second, third, second));
        var service = new TrainingResultService(albums, photos, training);
        assertThat(service.complete(10L, new float[]{1, 0}, 0.75)).isEqualTo(2);
        assertThat(album.getPhotos()).extracting(Photo::getId).containsExactly(1L, 2L, 3L);
        assertThat(service.complete(10L, new float[]{1, 0}, 0.75)).isZero();
        verify(photos, times(2)).findAllByVectorSimilarity(anyString(), eq(0.25));
    }

    @Test
    void rejectsAnAlbumContainingOnlyDeletedSamples() {
        AlbumRepository albums = mock(AlbumRepository.class);
        Album album = new Album();
        Photo deleted = photo(1L);
        deleted.setDeletedAt(java.time.LocalDateTime.now());
        album.getPhotos().add(deleted);
        when(albums.findWithPhotos(10L)).thenReturn(Optional.of(album));
        var service = new TrainingResultService(albums, mock(PhotoRepository.class), mock(TrainingSetRepository.class));
        assertThatThrownBy(() -> service.samples(10L)).isInstanceOf(IllegalArgumentException.class);
    }

    private Photo photo(Long id) {
        Photo photo = new Photo();
        photo.setId(id);
        return photo;
    }
}
