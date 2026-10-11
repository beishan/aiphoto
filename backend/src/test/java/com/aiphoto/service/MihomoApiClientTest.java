package com.aiphoto.service;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MihomoApiClientTest {
    @Test void switchesEncodedNodeGroupAndConfirmsSelectionUsingBearerToken() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> selected = new AtomicReference<>("old");
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> auth = new AtomicReference<>();
        server.createContext("/proxies", exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body;
            if (exchange.getRequestMethod().equals("PUT")) {
                path.set(exchange.getRequestURI().getRawPath());
                selected.set(new ObjectMapper().readTree(exchange.getRequestBody()).path("name").asText());
                body = "{}".getBytes(StandardCharsets.UTF_8);
            } else {
                body = ("{\"proxies\":{\"Crawl group\":{\"now\":\"" + selected.get() + "\"}}}").getBytes(StandardCharsets.UTF_8);
            }
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var client = new MihomoApiClient(new ObjectMapper());
            client.select("http://127.0.0.1:" + server.getAddress().getPort(), "token", "Crawl group", "new node");
            assertThat(path.get()).isEqualTo("/proxies/Crawl%20group");
            assertThat(auth.get()).isEqualTo("Bearer token");
            assertThat(selected.get()).isEqualTo("new node");
        } finally { server.stop(0); }
    }

    @Test void remoteErrorBodyDoesNotLeakToApiException() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/proxies", exchange -> {
            byte[] body = "sensitive remote body".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var client = new MihomoApiClient(new ObjectMapper());
            assertThatThrownBy(() -> client.proxies("http://127.0.0.1:" + server.getAddress().getPort(), "token"))
                    .isInstanceOf(MihomoApiClient.ApiException.class).hasMessage("Mihomo API 返回 HTTP 401");
        } finally { server.stop(0); }
    }
}
