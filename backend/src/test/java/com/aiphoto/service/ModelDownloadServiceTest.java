package com.aiphoto.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelDownloadServiceTest {
    @TempDir Path directory;

    @Test
    void schedulesDownloadAndTruncatesWhenServerIgnoresRange() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] body = new byte[]{1, 2, 3, 4};
        server.createContext("/weights", exchange -> {
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            ModelCatalogService catalog = mock(ModelCatalogService.class);
            ModelManagementService management = mock(ModelManagementService.class);
            when(catalog.getOnlineModels()).thenReturn(List.of(Map.of(
                    "id", "yolov8n", "name", "YOLO", "typeKey", "object_detection",
                    "filename", "yolov8n.pt", "url", "http://127.0.0.1:" + server.getAddress().getPort() + "/weights")));
            Path partial = directory.resolve("weights.part");
            Files.write(partial, new byte[]{9, 9});
            when(management.prepareDownloadedTarget(anyString())).thenReturn(partial);
            List<Runnable> queue = new ArrayList<>();
            var service = new ModelDownloadService(catalog, management, mock(ModelArtifactService.class), queue::add);
            var task = service.startDownload("yolov8n", "ignored", "ignored", "ignored", 999);
            assertThat(task.getStatus()).isEqualTo(ModelDownloadService.DownloadStatus.PENDING);
            assertThat(queue).hasSize(1);
            queue.remove(0).run();
            assertThat(task.getStatus()).isEqualTo(ModelDownloadService.DownloadStatus.COMPLETED);
            assertThat(Files.readAllBytes(partial)).containsExactly(body);
            assertThat(task.getTotalSize()).isEqualTo(4);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void onlyReportsInstalledAfterTheInferenceServiceLoadsTheModel() throws Exception {
        ModelCatalogService catalog = mock(ModelCatalogService.class);
        ModelManagementService management = mock(ModelManagementService.class);
        ModelArtifactService artifacts = mock(ModelArtifactService.class);
        var service = new ModelDownloadService(catalog, management, artifacts, action -> {});
        var task = new ModelDownloadService.DownloadTask();
        task.setTaskId("dl-example");
        task.setTypeKey("image_vector");
        task.setInstalledPath("installed/dl-example/model.pt");
        task.setStatus(ModelDownloadService.DownloadStatus.COMPLETED);
        when(catalog.getOnlineModels()).thenReturn(List.of(Map.of("id", "clip", "name", "clip",
                "typeKey", "image_vector", "filename", "model.pt", "url", "https://example.com/model.pt")));
        var actual = service.startDownload("clip", "clip", "image_vector", "", 0);
        actual.setInstalledPath(task.getInstalledPath());
        actual.setStatus(ModelDownloadService.DownloadStatus.COMPLETED);
        when(catalog.getAiServiceModelName("image_vector")).thenReturn("clip");
        when(management.configure("clip", task.getInstalledPath(), true))
                .thenReturn(Map.of("loaded", false, "error", "invalid checkpoint"));
        assertThatThrownBy(() -> service.setAsCurrent(actual.getTaskId()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("invalid checkpoint");
        assertThat(actual.getStatus()).isEqualTo(ModelDownloadService.DownloadStatus.COMPLETED);
        when(management.configure("clip", task.getInstalledPath(), true)).thenReturn(Map.of("loaded", true));
        service.setAsCurrent(actual.getTaskId());
        assertThat(actual.getStatus()).isEqualTo(ModelDownloadService.DownloadStatus.INSTALLED);
    }
}
