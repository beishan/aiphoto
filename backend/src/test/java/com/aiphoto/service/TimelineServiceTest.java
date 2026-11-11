package com.aiphoto.service;

import com.aiphoto.repository.PhotoRepository;
import com.aiphoto.storage.LocalStorageService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class TimelineServiceTest {
  @Test void monthQueryUsesHalfOpenLeapMonthAndBoundedPagination() {
    var photos = mock(PhotoRepository.class);
    when(photos.timelineMonth(any(), any(), any())).thenReturn(Page.empty());
    var service = new TimelineService(photos, mock(LocalStorageService.class));
    assertTrue(service.month(2024, 2, -1, 5000).isEmpty());
    verify(photos).timelineMonth(eq(LocalDateTime.of(2024, 2, 1, 0, 0)), eq(LocalDateTime.of(2024, 3, 1, 0, 0)),
        argThat(page -> page.getPageSize() == 200 && page.getPageNumber() == 0));
    verify(photos, never()).findTimelinePhotos();
  }
}
