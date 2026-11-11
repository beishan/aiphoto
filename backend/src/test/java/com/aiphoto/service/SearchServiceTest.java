package com.aiphoto.service;

import com.aiphoto.ai.AiServiceClient;
import com.aiphoto.dto.SearchRequest;
import com.aiphoto.repository.PhotoRepository;
import com.aiphoto.repository.PhotoTagRepository;
import com.aiphoto.storage.LocalStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SearchServiceTest {
  @Test
  void filtersUseDatabasePaginationWithoutLoadingLibraryOrTagIds() {
    PhotoRepository photos = mock(PhotoRepository.class);
    PhotoTagRepository tags = mock(PhotoTagRepository.class);
    when(photos.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
    SearchService service = new SearchService(photos, tags, mock(LocalStorageService.class), mock(AiServiceClient.class), mock(SettingService.class));
    SearchRequest request = new SearchRequest();
    request.setTagId(5L);
    request.setPage(100);
    request.setSize(5000);
    assertTrue(service.search(request, 1L).isEmpty());
    verify(photos).findAll(any(Specification.class), argThat((Pageable page) -> page.getPageNumber() == 100 && page.getPageSize() == 200));
    verify(photos, never()).findAll();
    verifyNoInteractions(tags);
  }
}
