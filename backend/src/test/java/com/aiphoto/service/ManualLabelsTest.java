package com.aiphoto.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aiphoto.ai.AiServiceClient;
import com.aiphoto.async.IndexResultService;
import com.aiphoto.entity.*;
import com.aiphoto.repository.*;
import com.aiphoto.storage.LocalStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ManualLabelsTest {
  @Test void explicitAdditionPromotesAiLinkAndClearsSuppression() {
    var tags = mock(TagRepository.class); var links = mock(PhotoTagRepository.class);
    var photos = mock(PhotoRepository.class); var exclusions = mock(PhotoTagExclusionRepository.class);
    var tag = new Tag(); tag.setId(2L); tag.setType(Tag.TagType.AI);
    var link = new PhotoTag(); link.setSource(Tag.TagType.AI); link.setPhotoId(1L); link.setTagId(2L);
    var id = new PhotoTagId(); id.setPhotoId(1L); id.setTagId(2L);
    when(photos.findForIndexing(1L)).thenReturn(Optional.of(new Photo()));
    when(tags.findById(2L)).thenReturn(Optional.of(tag)); when(links.findById(id)).thenReturn(Optional.of(link));
    new TagService(tags, links, photos, mock(LocalStorageService.class), exclusions).addTagToPhoto(1L, 2L);
    assertEquals(Tag.TagType.MANUAL, link.getSource()); assertEquals(1.0, link.getConfidence());
    verify(exclusions).deleteById(id); verify(links).save(link);
  }
  @Test void removingTagRecordsSuppressionAndAiCannotRestoreIt() {
    var tags = mock(TagRepository.class); var links = mock(PhotoTagRepository.class);
    var photos = mock(PhotoRepository.class); var exclusions = mock(PhotoTagExclusionRepository.class);
    var tag = new Tag(); tag.setId(2L); tag.setName("dog"); tag.setType(Tag.TagType.AI);
    var photo = new Photo(); photo.setId(1L);
    when(photos.findForIndexing(1L)).thenReturn(Optional.of(photo)); when(tags.findById(2L)).thenReturn(Optional.of(tag));
    var id = new PhotoTagId(); id.setPhotoId(1L); id.setTagId(2L);
    new TagService(tags, links, photos, mock(LocalStorageService.class), exclusions).removeTagFromPhoto(1L, 2L);
    verify(exclusions).save(argThat(e -> e.getPhotoId() == 1L && e.getTagId() == 2L)); verify(links).deleteById(id);
    when(tags.findByName("dog")).thenReturn(Optional.of(tag)); when(exclusions.existsById(id)).thenReturn(true);
    var result = new AiServiceClient.ClassifyResponse.TagResult(); result.setName("dog"); result.setConfidence(0.9);
    var response = new AiServiceClient.ClassifyResponse(); response.setTags(List.of(result));
    new IndexResultService(photos, mock(FaceClusterRepository.class), mock(PersonRepository.class), tags, links,
        mock(CategoryRepository.class), new ObjectMapper(), exclusions).saveTags(1L, response);
    verify(links, never()).save(any()); assertTrue(photo.isTagsIndexed());
  }
  @Test void aiVocabularyAndSelfMergeAreRejected() {
    var tags = mock(TagRepository.class); var tag = new Tag(); tag.setType(Tag.TagType.AI);
    when(tags.findById(2L)).thenReturn(Optional.of(tag));
    var service = new TagService(tags, mock(PhotoTagRepository.class), mock(PhotoRepository.class), mock(LocalStorageService.class), mock(PhotoTagExclusionRepository.class));
    assertThrows(IllegalArgumentException.class, () -> service.updateTag(2L, "rename", null, null, null));
    assertThrows(IllegalArgumentException.class, () -> service.mergeTags(2L, 2L));
    assertThrows(IllegalArgumentException.class, () -> service.createTag("  ", "#123456", null));
    verify(tags, never()).save(any());
  }
  @Test void categoryAdditionUsesManualUpsertAndRemovalDoesNotRewriteOtherLinks() {
    var categories = mock(CategoryRepository.class); var photos = mock(PhotoRepository.class);
    var category = new Category(); category.setId(4L); category.setIsSystem(false);
    when(categories.findById(4L)).thenReturn(Optional.of(category)); when(photos.findById(1L)).thenReturn(Optional.of(new Photo()));
    var service = new CategoryService(categories, photos, mock(PhotoTagRepository.class), mock(TagRepository.class), mock(LocalStorageService.class));
    service.addPhotoToCategory(4L, 1L); verify(categories).addManualPhoto(4L, 1L);
    service.removePhotoFromCategory(4L, 1L); verify(categories).removeManualPhoto(4L, 1L);
    verify(categories, never()).save(any());
    category.setIsSystem(true);
    assertThrows(IllegalArgumentException.class, () -> service.addPhotoToCategory(4L, 1L));
  }
}
