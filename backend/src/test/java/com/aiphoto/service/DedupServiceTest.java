package com.aiphoto.service;

import com.aiphoto.repository.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class DedupServiceTest {
  @Test void exactGroupsQueryOnlyDuplicateMembersWithBounds() {
    var photos = mock(PhotoRepository.class);
    when(photos.duplicateHashes(any())).thenReturn(List.of("hash"));
    when(photos.duplicateMembers(eq("hash"), any())).thenReturn(Page.empty());
    var service = new DedupService(photos, mock(AlbumRepository.class), mock(CategoryRepository.class));
    assertFalse(service.exactPage(2).hasMore());
    verify(photos).duplicateHashes(argThat(p -> p.getPageSize() == 20 && p.getOffset() == 40));
    verify(photos).duplicateMembers(eq("hash"), argThat(p -> p.getPageSize() == 100));
    verify(photos, never()).findAll();
  }
  @Test void indexedCandidatesAreVerifiedByBitDistanceAndCursorAdvances() {
    var photos = mock(PhotoRepository.class);
    var seed = new com.aiphoto.entity.Photo(); seed.setId(5L); seed.setFileHashPhash("0000000000000000");
    var match = new com.aiphoto.entity.Photo(); match.setId(6L); match.setFileHashPhash("00000000000000ff");
    var rejected = new com.aiphoto.entity.Photo(); rejected.setId(7L); rejected.setFileHashPhash("000000000000ffff");
    when(photos.similaritySeeds(0, 21)).thenReturn(List.of(seed));
    when(photos.similarityCandidates(5L, seed.getFileHashPhash())).thenReturn(List.of(match, rejected));
    var service = new DedupService(photos, mock(AlbumRepository.class), mock(CategoryRepository.class));
    var result = service.similarPage(0);
    assertEquals(5L, result.nextCursor());
    assertEquals(List.of(seed, match), result.groups().get(0));
    assertFalse(result.hasMore());
    verify(photos, never()).findAllWithPhash();
  }

  @Test void largeSimilarityScanIsRejectedBeforeLoadingPhotos() {
    var photos = mock(PhotoRepository.class);
    when(photos.countWithPhash()).thenReturn(2000000L);
    var service = new DedupService(photos, mock(AlbumRepository.class), mock(CategoryRepository.class));
    assertThrows(org.springframework.web.server.ResponseStatusException.class, service::findSimilarPhotos);
    verify(photos, never()).findAllWithPhash();
    verify(photos, never()).findPhashCandidates(any());
  }
}
