package com.aiphoto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiphoto.repository.PhotoRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class TrashCleanupServiceTest {

    @Mock
    private PhotoRepository photoRepository;

    @Mock
    private PhotoService photoService;

    private TrashCleanupService trashCleanupService;

    @BeforeEach
    void setUp() {
        trashCleanupService = new TrashCleanupService(photoRepository, photoService);
    }

    @Test
    void continuesDeletingAndCountsResultsWhenOnePhotoFails() {
        when(photoRepository.findTrashIds()).thenReturn(List.of(11L, 12L, 13L));
        doAnswer(invocation -> {
            if (invocation.getArgument(0, Long.class).equals(12L)) {
                throw new RuntimeException("database failure");
            }
            return null;
        }).when(photoService).permanentDeletePhoto(anyLong());

        TrashCleanupService.TrashCleanupResult result = trashCleanupService.clearTrash();

        assertThat(result.success()).isEqualTo(2);
        assertThat(result.fail()).isEqualTo(1);
        InOrder deletionOrder = inOrder(photoService);
        deletionOrder.verify(photoService).permanentDeletePhoto(11L);
        deletionOrder.verify(photoService).permanentDeletePhoto(12L);
        deletionOrder.verify(photoService).permanentDeletePhoto(13L);
    }

    @Test
    void returnsZeroCountsWithoutDeletingWhenTrashIsEmpty() {
        when(photoRepository.findTrashIds()).thenReturn(List.of());

        TrashCleanupService.TrashCleanupResult result = trashCleanupService.clearTrash();

        assertThat(result.success()).isZero();
        assertThat(result.fail()).isZero();
        verify(photoService, never()).permanentDeletePhoto(anyLong());
    }

    @Test
    void permanentDeletionAlwaysUsesAnIndependentTransaction() throws NoSuchMethodException {
        Transactional transactional = PhotoService.class
                .getMethod("permanentDeletePhoto", Long.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
