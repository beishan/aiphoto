package com.aiphoto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

class SafeHttpFetcherTest {

    @Test
    @SuppressWarnings("unchecked")
    void imageRetriesShareOneDeadlineAndDoNotLeaveTheWorkerInterrupted() throws Exception {
        var settings = org.mockito.Mockito.mock(CrawlerSettingsService.class);
        var executors = org.mockito.Mockito.mock(CrawlerExecutorService.class);
        var fetcher = new SafeHttpFetcher(settings, executors);
        var client = org.mockito.Mockito.mock(HttpClient.class);
        var session = org.mockito.Mockito.mock(CrawlerExecutorService.Session.class);
        org.mockito.Mockito.when(settings.networkSnapshot()).thenReturn(imageNetwork(1, 5));
        org.mockito.Mockito.when(executors.current()).thenReturn(session);
        org.mockito.Mockito.when(session.network()).thenReturn(imageNetwork(1, 5));
        org.mockito.Mockito.when(session.autoFailover()).thenReturn(true);
        org.mockito.Mockito.when(session.nodeCount()).thenReturn(4);
        var clients = (java.util.Map<String, HttpClient>)
                org.springframework.test.util.ReflectionTestUtils.getField(fetcher, "clients");
        clients.put("direct:5", client);
        org.mockito.Mockito.when(client.send(org.mockito.ArgumentMatchers.any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenAnswer(invocation -> {
                    Thread.sleep(700);
                    throw new java.io.IOException("temporary failure");
                });

        long started = System.nanoTime();
        assertThatThrownBy(() -> fetcher.fetchImage("https://8.8.8.8/image.jpg", "", 100, 0))
                .isInstanceOf(java.net.http.HttpTimeoutException.class)
                .hasMessageContaining("总时限 1 秒");
        assertThat(Duration.ofNanos(System.nanoTime() - started).toMillis()).isLessThan(2500);
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
        org.mockito.Mockito.verify(session, org.mockito.Mockito.never()).waitIfUnavailable();
        org.mockito.Mockito.verify(client, org.mockito.Mockito.atMost(2)).send(
                org.mockito.ArgumentMatchers.any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shorteningTheSettingInterruptsAnImageAlreadyDownloading() throws Exception {
        var settings = org.mockito.Mockito.mock(CrawlerSettingsService.class);
        var executors = org.mockito.Mockito.mock(CrawlerExecutorService.class);
        var fetcher = new SafeHttpFetcher(settings, executors);
        var timeout = new java.util.concurrent.atomic.AtomicInteger(30);
        var started = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (var body = exchange.getResponseBody()) {
                body.write(new byte[] {1});
                body.flush();
                started.countDown();
                release.await(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        var client = HttpClient.newBuilder().proxy(java.net.ProxySelector.of(server.getAddress())).build();
        org.mockito.Mockito.when(settings.networkSnapshot())
                .thenAnswer(invocation -> imageNetwork(timeout.get(), 5));
        var clients = (java.util.Map<String, HttpClient>)
                org.springframework.test.util.ReflectionTestUtils.getField(fetcher, "clients");
        clients.put("direct:5", client);
        try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var result = workers.submit(() -> {
                try {
                    fetcher.fetchImage("http://8.8.8.8/image.jpg", "", 100, 0);
                    return (Exception) null;
                } catch (Exception exception) {
                    assertThat(Thread.currentThread().isInterrupted()).isFalse();
                    return exception;
                }
            });
            try {
                assertThat(started.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                timeout.set(1);
                assertThat(result.get(3, java.util.concurrent.TimeUnit.SECONDS))
                        .isInstanceOf(java.net.http.HttpTimeoutException.class)
                        .hasMessageContaining("总时限 1 秒");
            } finally {
                result.cancel(true);
                release.countDown();
            }
        } finally {
            release.countDown();
            server.stop(0);
            client.shutdownNow();
        }
    }

    private CrawlerSettingsService.NetworkSnapshot imageNetwork(int timeout, int retries) {
        return new CrawlerSettingsService.NetworkSnapshot(true, 5, 30, timeout,
                0, retries, 0, java.util.List.of());
    }

    @Test
    void requestTimeoutAlsoStopsAResponseThatStallsDuringImageBodyDownload() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/slow-image", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (var body = exchange.getResponseBody()) {
                body.write(new byte[] {1});
                body.flush();
                Thread.sleep(3000);
                body.write(new byte[] {2});
            } catch (java.io.IOException ignored) {
                // The client closes the response when its request timeout expires.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/slow-image"))
                    .timeout(Duration.ofMillis(300)).GET().build();
            long started = System.nanoTime();
            assertThatThrownBy(() -> SafeHttpFetcher.sendWithBodyTimeout(
                    HttpClient.newHttpClient(), request, 100, 1))
                    .isInstanceOf(java.net.http.HttpTimeoutException.class)
                    .hasMessageContaining("响应体下载超过 1 秒");
            assertThat(Duration.ofNanos(System.nanoTime() - started).toMillis()).isLessThan(2200);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void imageDownloadsNegotiateImageBytesWhilePageRequestsStillAcceptHtml() throws Exception {
        var settings = org.mockito.Mockito.mock(CrawlerSettingsService.class);
        var executors = org.mockito.Mockito.mock(CrawlerExecutorService.class);
        var fetcher = new SafeHttpFetcher(settings, executors);
        var client = org.mockito.Mockito.mock(java.net.http.HttpClient.class);
        var network = new CrawlerSettingsService.NetworkSnapshot(true, 5, 10, 60, 0, 0, 0, java.util.List.of());
        org.mockito.Mockito.when(settings.networkSnapshot()).thenReturn(network);
        var clients = (java.util.Map<String, java.net.http.HttpClient>)
                org.springframework.test.util.ReflectionTestUtils.getField(fetcher, "clients");
        clients.put("direct:5", client);
        org.mockito.Mockito.when(client.send(org.mockito.ArgumentMatchers.any(java.net.http.HttpRequest.class),
                org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<byte[]>>any()))
                .thenAnswer(invocation -> {
                    java.net.http.HttpRequest request = invocation.getArgument(0);
                    boolean image = request.headers().firstValue("Accept").orElse("").startsWith("image/");
                    assertThat(request.timeout()).contains(java.time.Duration.ofSeconds(image ? 60 : 10));
                    assertThat(request.headers().firstValue("Referer")).isEmpty();
                    java.net.http.HttpResponse<byte[]> response = org.mockito.Mockito.mock(java.net.http.HttpResponse.class);
                    org.mockito.Mockito.when(response.statusCode()).thenReturn(200);
                    org.mockito.Mockito.when(response.headers()).thenReturn(java.net.http.HttpHeaders.of(
                            java.util.Map.of("content-type", java.util.List.of(image ? "image/jpeg" : "text/html")), (a, b) -> true));
                    org.mockito.Mockito.when(response.body()).thenReturn(
                            image ? new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff} : "<img src='/photo.jpg'>".getBytes());
                    return response;
                });
        assertThat(fetcher.fetch("https://8.8.8.8/photo.jpg", "", 100, 0).contentType()).isEqualTo("text/html");
        var original = fetcher.fetchImage("https://8.8.8.8/photo.jpg", "", 100, 0);
        assertThat(original.contentType()).isEqualTo("image/jpeg");
        assertThat(original.bytes()).startsWith((byte) 0xff, (byte) 0xd8, (byte) 0xff);
    }

    @Test
    @SuppressWarnings("unchecked")
    void imageRedirectsKeepImageAcceptAndCannotVisitBlockedHosts() throws Exception {
        var settings = org.mockito.Mockito.mock(CrawlerSettingsService.class);
        var executors = org.mockito.Mockito.mock(CrawlerExecutorService.class);
        var fetcher = new SafeHttpFetcher(settings, executors);
        var client = org.mockito.Mockito.mock(java.net.http.HttpClient.class);
        org.mockito.Mockito.when(settings.networkSnapshot()).thenReturn(
                new CrawlerSettingsService.NetworkSnapshot(true, 5, 10, 60, 0, 0, 0, java.util.List.of()));
        var clients = (java.util.Map<String, java.net.http.HttpClient>)
                org.springframework.test.util.ReflectionTestUtils.getField(fetcher, "clients");
        clients.put("direct:5", client);
        org.mockito.Mockito.when(client.send(org.mockito.ArgumentMatchers.any(java.net.http.HttpRequest.class),
                org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<byte[]>>any()))
                .thenAnswer(invocation -> {
                    java.net.http.HttpRequest request = invocation.getArgument(0);
                    assertThat(request.headers().firstValue("Accept").orElse("")).startsWith("image/").doesNotContain("text/html");
                    assertThat(request.headers().firstValue("Referer")).isEmpty();
                    boolean redirect = "/first".equals(request.uri().getPath());
                    java.net.http.HttpResponse<byte[]> response = org.mockito.Mockito.mock(java.net.http.HttpResponse.class);
                    org.mockito.Mockito.when(response.statusCode()).thenReturn(redirect ? 302 : 200);
                    org.mockito.Mockito.when(response.headers()).thenReturn(java.net.http.HttpHeaders.of(redirect
                            ? java.util.Map.of("location", java.util.List.of("https://8.8.4.4/original.jpg"))
                            : java.util.Map.of("content-type", java.util.List.of("image/jpeg")), (a, b) -> true));
                    org.mockito.Mockito.when(response.body()).thenReturn(new byte[] {1});
                    return response;
                });
        assertThat(fetcher.fetchImage("https://8.8.8.8/first", "", 100, 0).finalUri())
                .isEqualTo(URI.create("https://8.8.4.4/original.jpg"));
        assertThatThrownBy(() -> fetcher.fetchImage("https://8.8.8.8/first", "8.8.4.4", 100, 0))
                .hasMessageContaining("禁止访问列表");
    }

    @Test
    void normalizesDefaultPortAndRemovesFragment() {
        assertThat(SafeHttpFetcher.normalize("HTTPS://Example.COM:443/a/../photo?q=1#part"))
                .isEqualTo("https://example.com/photo?q=1");
    }

    @Test
    void hashesNormalizedUrlsDeterministically() {
        String first = SafeHttpFetcher.urlHash("https://example.com/photo/1");
        String second = SafeHttpFetcher.urlHash("https://example.com/photo/1");

        assertThat(first).hasSize(64).isEqualTo(second);
    }

    @Test void emptyBlacklistAllowsPublicTargets() throws Exception {
        SafeHttpFetcher.validate(URI.create("https://8.8.8.8/image"), Set.of());
    }

    @Test void blocksRedirectDestinationsBeforeRequestingThem() throws Exception {
        var settings = org.mockito.Mockito.mock(CrawlerSettingsService.class);
        var executors = org.mockito.Mockito.mock(CrawlerExecutorService.class);
        var fetcher = new SafeHttpFetcher(settings, executors);
        var client = org.mockito.Mockito.mock(java.net.http.HttpClient.class);
        @SuppressWarnings("unchecked")
        java.net.http.HttpResponse<byte[]> response = org.mockito.Mockito.mock(java.net.http.HttpResponse.class);
        org.mockito.Mockito.when(response.statusCode()).thenReturn(302);
        org.mockito.Mockito.when(response.body()).thenReturn(new byte[0]);
        org.mockito.Mockito.when(response.headers()).thenReturn(java.net.http.HttpHeaders.of(
                java.util.Map.of("location", java.util.List.of("https://cdn.66img.cc/image.jpg")), (a, b) -> true));
        org.mockito.Mockito.when(client.send(org.mockito.ArgumentMatchers.any(java.net.http.HttpRequest.class),
                org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(response);
        @SuppressWarnings("unchecked")
        var clients = (java.util.Map<String, java.net.http.HttpClient>)
                org.springframework.test.util.ReflectionTestUtils.getField(fetcher, "clients");
        clients.put("direct:5", client);
        var network = new CrawlerSettingsService.NetworkSnapshot(true, 5, 10, 60, 0, 0, 0, java.util.List.of());
        assertThatThrownBy(() -> fetcher.fetchForInspection("http://8.8.8.8/", "66img.cc", 100, 0, network))
                .hasMessageContaining("禁止访问列表");
        org.mockito.Mockito.verify(client, org.mockito.Mockito.times(1)).send(
                org.mockito.ArgumentMatchers.any(java.net.http.HttpRequest.class),
                org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<byte[]>>any());
    }

    @Test
    void rejectsLoopbackTargetsWithAnEmptyBlacklist() {
        assertThatThrownBy(() -> SafeHttpFetcher.validate(
                URI.create("http://127.0.0.1/image"), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非公网地址");
    }

    @Test
    void rejectsPrivateIpv6Targets() {
        assertThatThrownBy(() -> SafeHttpFetcher.validate(
                URI.create("http://[fd00::1]/image"), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCredentialsAndNonStandardPorts() {
        assertThatThrownBy(() -> SafeHttpFetcher.validate(
                URI.create("https://user:pass@example.com/"), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SafeHttpFetcher.validate(
                URI.create("https://example.com:8443/"), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retriesOnlyTemporaryHttpFailures() {
        assertThat(SafeHttpFetcher.isRetryableStatus(429)).isTrue();
        assertThat(SafeHttpFetcher.isRetryableStatus(503)).isTrue();
        assertThat(SafeHttpFetcher.isRetryableStatus(404)).isFalse();
        assertThat(SafeHttpFetcher.isRetryableStatus(401)).isFalse();
    }

    @Test
    void usesExponentialBackoffAndHonorsRetryAfter() {
        assertThat(SafeHttpFetcher.retryDelayMillis(0, 1000, null)).isEqualTo(1000);
        assertThat(SafeHttpFetcher.retryDelayMillis(2, 1000, null)).isEqualTo(4000);
        assertThat(SafeHttpFetcher.retryDelayMillis(0, 1000, "5")).isEqualTo(5000);
        assertThat(SafeHttpFetcher.retryDelayMillis(10, 1000, "invalid")).isEqualTo(60_000);
    }
    @Test
    void inspectionDirectDoesNotConsultOrUpdateTaskExecutor() throws Exception {
        assertInspectionRoute(true, 200);
    }

    @Test
    void inspectionSelectedDoesNotConsultOrUpdateTaskExecutor() throws Exception {
        assertInspectionRoute(false, 200);
    }

    @Test
    void inspectionSelectedFailureNeverFallsBackToDirect() throws Exception {
        assertInspectionRoute(false, 502);
    }

    @SuppressWarnings("unchecked")
    private void assertInspectionRoute(boolean direct, int status) throws Exception {
        var settings = org.mockito.Mockito.mock(CrawlerSettingsService.class);
        var executors = org.mockito.Mockito.mock(CrawlerExecutorService.class);
        var fetcher = new SafeHttpFetcher(settings, executors);
        var selectedClient = org.mockito.Mockito.mock(java.net.http.HttpClient.class);
        var otherClient = org.mockito.Mockito.mock(java.net.http.HttpClient.class);
        java.net.http.HttpResponse<byte[]> response = org.mockito.Mockito.mock(java.net.http.HttpResponse.class);
        org.mockito.Mockito.when(response.statusCode()).thenReturn(status);
        org.mockito.Mockito.when(response.body()).thenReturn(new byte[] {1});
        if (status == 200) {
            org.mockito.Mockito.when(response.headers()).thenReturn(
                    java.net.http.HttpHeaders.of(java.util.Map.of("content-type", java.util.List.of("text/html")), (a, b) -> true));
        }
        org.mockito.Mockito.when(selectedClient.send(
                org.mockito.ArgumentMatchers.any(java.net.http.HttpRequest.class),
                org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(response);
        var clients = (java.util.Map<String, java.net.http.HttpClient>)
                org.springframework.test.util.ReflectionTestUtils.getField(fetcher, "clients");
        String proxyKey = "proxy:7:localhost:7890:null:0:5";
        clients.put(direct ? "direct:5" : proxyKey, selectedClient);
        clients.put(direct ? proxyKey : "direct:5", otherClient);
        var network = new CrawlerSettingsService.NetworkSnapshot(direct, 5, 10, 60, 0, 0, 0,
                direct ? java.util.List.of() : java.util.List.of(
                        new CrawlerSettingsService.ProxyConfig(7L, "localhost", 7890, null, null)));

        if (status == 200) {
            assertThat(fetcher.fetchForInspection("http://8.8.8.8/", "", 10, 0, network).bytes())
                    .containsExactly((byte) 1);
        } else {
            assertThatThrownBy(() -> fetcher.fetchForInspection("http://8.8.8.8/", "", 10, 0, network))
                    .isInstanceOf(java.io.IOException.class);
        }
        org.mockito.Mockito.verifyNoInteractions(settings, executors, otherClient);
    }
}
