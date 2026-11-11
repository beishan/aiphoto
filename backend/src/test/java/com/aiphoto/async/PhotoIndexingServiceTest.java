package com.aiphoto.async;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.aiphoto.ai.AiServiceClient;
import com.aiphoto.entity.AiTask;
import com.aiphoto.entity.Photo;
import com.aiphoto.repository.*;
import com.aiphoto.service.SettingService;
import com.aiphoto.storage.LocalStorageService;
import com.aiphoto.websocket.ProgressWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PhotoIndexingServiceTest {
    @Test
    void clipFailureDoesNotBlockOtherStagesAndRetriesSkipSavedFaces() throws Exception {
        AiServiceClient client = mock(AiServiceClient.class);
        PhotoRepository photos = mock(PhotoRepository.class);
        AiTaskRepository tasks = mock(AiTaskRepository.class);
        LocalStorageService storage = mock(LocalStorageService.class);
        IndexResultService writer = mock(IndexResultService.class);
        SettingService settings = mock(SettingService.class);
        Photo photo = new Photo();
        photo.setId(1L);
        photo.setFilePath("image.jpg");
        photo.setAiOwnerId(42L);
        photo.setOriginalFilename("image.jpg");
        AiTask task = new AiTask();
        task.setId(2L);
        task.setType(AiTask.TaskType.INDEX);
        var models = new AiServiceClient.ModelOverview();
        models.setModels(List.of(state("clip"), state("insightface"), state("yolo")));
        when(client.modelStatus()).thenReturn(models);
        when(tasks.findById(2L)).thenReturn(Optional.of(task));
        when(photos.findById(1L)).thenReturn(Optional.of(photo));
        byte[] data = new byte[]{1};
        when(storage.downloadBytes("image.jpg")).thenReturn(data);
        when(client.embed(data, "image.jpg")).thenThrow(new IllegalStateException("missing weights"));
        var faces = new AiServiceClient.FaceDetectionResponse();
        faces.setFaces(List.of());
        when(client.detectFaces(data, "image.jpg")).thenReturn(faces);
        var tags = new AiServiceClient.ClassifyResponse();
        tags.setTags(List.of());
        when(client.classify(data, "image.jpg")).thenReturn(tags);
        when(settings.getSetting(42L, "ai_face_cluster_threshold")).thenReturn("80");
        var service = new PhotoIndexingService(client, photos, tasks,
                mock(ProgressWebSocketHandler.class), storage, settings, writer, new ObjectMapper());
        service.indexPhotos(2L, List.of(1L));
        verify(writer).saveFaces(eq(1L), eq(faces), doubleThat(value -> Math.abs(value - 0.2) < 1e-8));
        verify(writer).saveTags(1L, tags);
        verify(client, never()).caption(any(), any());
        assertThat(task.getStatus()).isEqualTo(AiTask.TaskStatus.FAILED);
        assertThat(task.getResultJson()).contains("clip", "missing weights");
        photo.setFacesIndexed(true);
        photo.setTagsIndexed(true);
        service.indexPhotos(2L, List.of(1L));
        verify(client, times(1)).detectFaces(data, "image.jpg");
        verify(client, times(1)).classify(data, "image.jpg");
    }

    @Test
    void pendingIndexAdvancesPastFailuresAndBoundsDetails() throws Exception {
        var client = mock(AiServiceClient.class);
        var photos = mock(PhotoRepository.class);
        var tasks = mock(AiTaskRepository.class);
        var models = new AiServiceClient.ModelOverview();
        models.setModels(List.of(state("clip")));
        when(client.modelStatus()).thenReturn(models);
        var task = new AiTask(); task.setId(7L); task.setType(AiTask.TaskType.INDEX);
        when(tasks.findById(7L)).thenReturn(Optional.of(task));
        var first = java.util.stream.LongStream.rangeClosed(1, 100).boxed().toList();
        when(photos.pendingIndexIds(eq(0L), eq(101L), eq(true), eq(false), eq(false), eq(false), any())).thenReturn(first);
        when(photos.pendingIndexIds(eq(100L), eq(101L), eq(true), eq(false), eq(false), eq(false), any())).thenReturn(List.of(101L));
        when(photos.pendingIndexIds(eq(101L), eq(101L), eq(true), eq(false), eq(false), eq(false), any())).thenReturn(List.of());
        var mapper = new ObjectMapper();
        var service = new PhotoIndexingService(client, photos, tasks, mock(ProgressWebSocketHandler.class),
                mock(LocalStorageService.class), mock(SettingService.class), mock(IndexResultService.class), mapper);
        service.indexPendingPhotos(7L, new PhotoIndexingService.IndexPlan(101, 101, true, false, false, false), 1L);
        var result = mapper.readTree(task.getResultJson());
        assertThat(result.get("failed").asInt()).isEqualTo(101);
        assertThat(result.get("failures").size()).isEqualTo(100);
        assertThat(result.get("failuresTruncated").asBoolean()).isTrue();
        assertThat(task.getProgress()).isEqualTo(100);
        verify(photos, times(3)).pendingIndexIds(anyLong(), eq(101L), eq(true), eq(false), eq(false), eq(false),
                argThat(page -> page.getPageSize() == 100 && page.getOffset() == 0));
    }

    private AiServiceClient.ModelState state(String name) {
        var state = new AiServiceClient.ModelState();
        state.setName(name);
        state.setEnabled(true);
        return state;
    }
}
