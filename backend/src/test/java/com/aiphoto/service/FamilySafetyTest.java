package com.aiphoto.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aiphoto.entity.*;
import com.aiphoto.repository.*;
import com.aiphoto.storage.LocalStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FamilySafetyTest {
  @Test void unrelatedOrDeletedFaceCannotBecomePersonCover() {
    var people = mock(PersonRepository.class); var faces = mock(FaceClusterRepository.class);
    var service = new FaceService(people, faces, mock(PhotoRepository.class), mock(LocalStorageService.class), mock(SettingService.class));
    var person = new Person(); person.setId(1L); var other = new Person(); other.setId(2L);
    var photo = new Photo(); var face = new FaceCluster(); face.setId(4L); face.setPhoto(photo); face.setPerson(other);
    when(people.findById(1L)).thenReturn(Optional.of(person)); when(faces.findById(4L)).thenReturn(Optional.of(face));
    assertThrows(IllegalArgumentException.class, () -> service.setCoverFace(1L, 4L));
    face.setPerson(person); photo.setDeletedAt(LocalDateTime.now());
    assertThrows(IllegalArgumentException.class, () -> service.setCoverFace(1L, 4L));
    verify(people, never()).save(person);
  }
  @Test void selfMergeFailsBeforeChangingAnyFace() {
    var people = mock(PersonRepository.class); var faces = mock(FaceClusterRepository.class);
    var service = new FaceService(people, faces, mock(PhotoRepository.class), mock(LocalStorageService.class), mock(SettingService.class));
    assertThrows(IllegalArgumentException.class, () -> service.mergePeople(1L, 1L));
    verifyNoInteractions(people, faces);
  }
  @Test void reassigningCoverClearsPreviousCoverAndRefreshesBothCounts() {
    var people = mock(PersonRepository.class); var faces = mock(FaceClusterRepository.class);
    var source = new Person(); source.setId(1L); var target = new Person(); target.setId(2L);
    var face = new FaceCluster(); face.setId(7L); face.setPerson(source); face.setPhoto(new Photo()); source.setCoverFace(face);
    when(people.findById(2L)).thenReturn(Optional.of(target)); when(faces.findById(7L)).thenReturn(Optional.of(face));
    new FaceService(people, faces, mock(PhotoRepository.class), mock(LocalStorageService.class), mock(SettingService.class)).assignFaceToPerson(7L, 2L);
    assertNull(source.getCoverFace()); assertSame(target, face.getPerson());
    verify(people).refreshPhotoCount(1L); verify(people).refreshPhotoCount(2L);
  }
  @Test void faceRegionHandlesPixelAndLegacyNormalizedCoordinates() throws Exception {
    var json = new ObjectMapper();
    assertEquals(new java.awt.Rectangle(10, 20, 30, 40), FacePreviewService.region(json.readTree("{\"x\":10,\"y\":20,\"w\":30,\"h\":40}"), 100, 100));
    assertEquals(new java.awt.Rectangle(10, 20, 30, 40), FacePreviewService.region(json.readTree("{\"x\":0.1,\"y\":0.2,\"w\":0.3,\"h\":0.4}"), 100, 100));
    assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> FacePreviewService.region(json.readTree("{\"x\":200,\"y\":20,\"w\":30,\"h\":40}"), 100, 100));
  }
}
