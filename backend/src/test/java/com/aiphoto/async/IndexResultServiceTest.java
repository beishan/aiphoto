package com.aiphoto.async;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.aiphoto.ai.AiServiceClient;
import com.aiphoto.entity.*;
import com.aiphoto.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IndexResultServiceTest {
    @Test
    void preservesExistingFacesAndDoesNotIncrementCountsOnRetry() {
        PhotoRepository photos = mock(PhotoRepository.class);
        FaceClusterRepository faces = mock(FaceClusterRepository.class);
        PersonRepository people = mock(PersonRepository.class);
        Photo photo = new Photo();
        photo.setId(1L);
        FaceCluster existing = new FaceCluster();
        Person person = new Person();
        person.setId(2L);
        existing.setPerson(person);
        when(photos.findForIndexing(1L)).thenReturn(Optional.of(photo));
        when(faces.findByPhoto(photo)).thenReturn(List.of(existing));
        var writer = new IndexResultService(photos, faces, people, mock(TagRepository.class),
                mock(PhotoTagRepository.class), mock(CategoryRepository.class), new ObjectMapper());
        var response = new AiServiceClient.FaceDetectionResponse();
        response.setFaces(List.of());
        writer.saveFaces(1L, response, 0.5);
        writer.saveFaces(1L, response, 0.5);
        assertThat(photo.isFacesIndexed()).isTrue();
        verify(faces, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
        verify(people, times(1)).refreshPhotoCount(2L);
    }

    @Test
    void rejectsInvalidVectorsBeforePersisting() {
        assertThatThrownBy(() -> IndexResultService.vector(List.of(1f, 2f)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IndexResultService.vector(java.util.Collections.nCopies(512, 0f)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IndexResultService.vector(java.util.Collections.nCopies(512, Float.NaN)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
