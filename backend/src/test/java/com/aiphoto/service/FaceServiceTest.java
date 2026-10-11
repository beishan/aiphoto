package com.aiphoto.service;

import static org.mockito.Mockito.*;
import com.aiphoto.entity.*;
import com.aiphoto.repository.*;
import com.aiphoto.storage.LocalStorageService;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FaceServiceTest {
    @Test
    void reassigningAFaceRefreshesBothPersonsUsingDistinctPhotoCounts() {
        var people = mock(PersonRepository.class);
        var faces = mock(FaceClusterRepository.class);
        Person original = new Person();
        original.setId(1L);
        Person target = new Person();
        target.setId(2L);
        FaceCluster face = new FaceCluster();
        face.setId(10L);
        face.setPerson(original);
        when(faces.findById(10L)).thenReturn(Optional.of(face));
        when(people.findById(2L)).thenReturn(Optional.of(target));
        var service = new FaceService(people, faces, mock(PhotoRepository.class),
                mock(LocalStorageService.class), mock(SettingService.class));
        service.assignFaceToPerson(10L, 2L);
        verify(faces).saveAndFlush(face);
        verify(people).refreshPhotoCount(1L);
        verify(people).refreshPhotoCount(2L);
    }
}
