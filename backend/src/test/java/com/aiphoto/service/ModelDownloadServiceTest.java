package com.aiphoto.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.aiphoto.entity.CrawlerProxy;
import com.aiphoto.entity.CrawlerSetting;
import com.aiphoto.repository.CrawlerProxyRepository;
import com.aiphoto.repository.CrawlerSettingRepository;
import com.sun.net.httpserver.HttpServer;
import java.util.Optional;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ModelDownloadServiceTest {
    @TempDir Path directory;

    @Timeout(30)
    @ParameterizedTest
    @ValueSource(strings = {"direct", "deployment", "system"})
    void schedulesDownloadAndTruncatesWhenServerIgnoresRange(String route) throws Exception {
        boolean viaProxy = !"direct".equals(route);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] body = new byte[]{1, 2, 3, 4};
        server.createContext("/weights", exchange -> {
            if ("system".equals(route) && !"Basic dGVzdDpzZWNyZXQ=".equals(
                    exchange.getRequestHeaders().getFirst("Proxy-Authorization"))) {
                exchange.getResponseHeaders().set("Proxy-Authenticate", "Basic realm=\"model-test\"");
                exchange.sendResponseHeaders(407, -1);
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            ModelCatalogService catalog = mock(ModelCatalogService.class);
            ModelManagementService management = mock(ModelManagementService.class);
            String localUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            String downloadUrl = viaProxy ? "http://models.invalid/weights" : localUrl + "/weights";
            when(catalog.getOnlineModels()).thenReturn(List.of(Map.of(
                    "id", "yolov8n", "name", "YOLO", "typeKey", "object_detection",
                    "filename", "yolov8n.pt", "url", downloadUrl)));
            Path partial = directory.resolve("weights.part");
            Files.write(partial, new byte[]{9, 9});
            when(management.prepareDownloadedTarget(anyString())).thenReturn(partial);
            List<Runnable> queue = new ArrayList<>();
            var settings = mock(CrawlerSettingRepository.class);
            var proxies = mock(CrawlerProxyRepository.class);
            var secrets = mock(CrawlerSecretService.class);
            var proxyService = new ModelDownloadProxyService(settings, proxies, secrets);
            var globalSetting = new CrawlerSetting();
            when(settings.findById(1L)).thenReturn(Optional.of(globalSetting));
            if ("system".equals(route)) {
                var proxy = new CrawlerProxy();
                proxy.setId(42L);
                proxy.setHost("127.0.0.1");
                proxy.setPort(server.getAddress().getPort());
                proxy.setUsername("test");
                proxy.setPasswordCiphertext("encrypted");
                when(secrets.decrypt("encrypted")).thenReturn("secret");
                when(proxies.findById(42L)).thenReturn(Optional.of(proxy));
                proxyService.select(42L);
                verify(settings).save(globalSetting);
                assertThat(proxyService.getConfiguration().proxyId()).isEqualTo(42L);
            }
            var service = new ModelDownloadService(catalog, management, mock(ModelArtifactService.class),
                    queue::add, "deployment".equals(route) ? localUrl : "", proxyService);
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
        List<Runnable> queue = new ArrayList<>();
        var service = new ModelDownloadService(catalog, management, artifacts, queue::add, "", mock(ModelDownloadProxyService.class));
        var task = new ModelDownloadService.DownloadTask();
        task.setTaskId("dl-example");
        task.setTypeKey("image_vector");
        task.setInstalledPath("installed/dl-example/model.pt");
        task.setStatus(ModelDownloadService.DownloadStatus.COMPLETED);
        when(catalog.getOnlineModels()).thenReturn(List.of(Map.of("id", "clip", "name", "clip",
                "typeKey", "image_vector", "filename", "model.pt", "url", "https://example.com/model.pt")));
        var actual = service.startDownload("clip", "clip", "image_vector", "", 0);
        queue.clear(); // Use the already-downloaded fixture below.
        actual.setInstalledPath(task.getInstalledPath());
        actual.setStatus(ModelDownloadService.DownloadStatus.COMPLETED);
        when(catalog.getAiServiceModelName("image_vector")).thenReturn("clip");
        when(management.configure("clip", task.getInstalledPath(), true))
                .thenReturn(Map.of("loaded", false, "error", "invalid checkpoint"));
        assertThat(service.setAsCurrent(actual.getTaskId()).getStatus())
                .isEqualTo(ModelDownloadService.DownloadStatus.INSTALLING);
        service.setAsCurrent(actual.getTaskId());
        assertThat(queue).hasSize(1);
        queue.removeFirst().run();
        assertThat(actual.getStatus()).isEqualTo(ModelDownloadService.DownloadStatus.COMPLETED);
        assertThat(actual.getErrorMessage()).contains("invalid checkpoint");
        when(management.configure("clip", task.getInstalledPath(), true)).thenReturn(Map.of("loaded", true));
        service.setAsCurrent(actual.getTaskId());
        queue.removeFirst().run();
        assertThat(actual.getStatus()).isEqualTo(ModelDownloadService.DownloadStatus.INSTALLED);
    }
}
