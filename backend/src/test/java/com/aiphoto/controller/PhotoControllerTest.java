package com.aiphoto.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.aiphoto.async.PhotoIndexingService;
import com.aiphoto.repository.AiTaskRepository;
import com.aiphoto.repository.PhotoRepository;
import com.aiphoto.repository.UserRepository;
import com.aiphoto.service.PhotoService;
import com.aiphoto.service.TrashCleanupService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class PhotoControllerTest {

    @Mock
    private PhotoService photoService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PhotoRepository photoRepository;

    @Mock
    private AiTaskRepository aiTaskRepository;

    @Mock
    private PhotoIndexingService photoIndexingService;

    @Mock
    private TrashCleanupService trashCleanupService;

    private PhotoController photoController;

    @BeforeEach
    void setUp() {
        photoController = new PhotoController(
                photoService,
                userRepository,
                photoRepository,
                aiTaskRepository,
                photoIndexingService,
                trashCleanupService);
    }

    @Test
    void clearTrashReturnsActualSuccessAndFailureCounts() {
        when(trashCleanupService.clearTrash())
                .thenReturn(new TrashCleanupService.TrashCleanupResult(4, 2));

        ResponseEntity<Map<String, Integer>> response = photoController.clearTrash();

        assertThat(response.getBody()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "success", 4,
                "fail", 2));
    }
}
