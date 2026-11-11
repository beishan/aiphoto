package com.aiphoto.service;

import com.aiphoto.entity.Photo;
import com.aiphoto.repository.PhotoRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class PhotoBrowseTest {
  @Test void boundsPageAndReturnsCursorFromLastVisiblePhoto() throws Exception {
    PhotoRepository repository = mock(PhotoRepository.class);
    PhotoService service = mock(PhotoService.class, CALLS_REAL_METHODS);
    var field = PhotoService.class.getDeclaredField("photoRepository");
    field.setAccessible(true);
    field.set(service, repository);
    Photo first = new Photo(); first.setId(9L);
    Photo second = new Photo(); second.setId(8L);
    var storageField = PhotoService.class.getDeclaredField("storageService");
    storageField.setAccessible(true);
    storageField.set(service, mock(com.aiphoto.storage.LocalStorageService.class));
    first.setOriginalFilename("first.jpg"); second.setOriginalFilename("second.jpg");
    when(repository.findBeforeId(eq(Long.MAX_VALUE), any(Pageable.class))).thenReturn(List.of(first, second));
    var initial = service.browsePhotos(null, 1);
    assertTrue(initial.hasMore());
    assertEquals(9L, initial.nextCursor());
    assertEquals(1, initial.content().size());
    when(repository.findBeforeId(eq(8L), any(Pageable.class))).thenReturn(List.of());
    var result = service.browsePhotos(8L, 5000);
    assertFalse(result.hasMore());
    assertNull(result.nextCursor());
    verify(repository).findBeforeId(eq(8L), argThat((Pageable p) -> p.getOffset() == 0 && p.getPageSize() == 201));
    verify(repository, never()).count();
  }
}
