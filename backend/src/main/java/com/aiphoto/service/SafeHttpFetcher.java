package com.aiphoto.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SafeHttpFetcher {

    private static final int MAX_REDIRECTS = 5;
    private final CrawlerSettingsService crawlerSettingsService;
    private final ConcurrentHashMap<String, Long> nextRequestAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, HttpClient> clients = new ConcurrentHashMap<>();

    public FetchedResource fetch(String url, String allowedHosts, long maxBytes) throws Exception {
        Set<String> hosts = parseHosts(allowedHosts);
        URI uri = URI.create(url);
        CrawlerSettingsService.NetworkSnapshot settings = crawlerSettingsService.networkSnapshot();
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            validate(uri, hosts);
            awaitRateLimit(uri.getHost(), settings.minRequestIntervalMillis());
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(settings.requestTimeoutSeconds()))
                    .header("User-Agent", "aiphoto-crawler/1.0")
                    .header("Accept", "text/html,image/*;q=0.9,*/*;q=0.1")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = sendWithRetry(request, settings);
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("location")
                        .orElseThrow(() -> new IllegalStateException("重定向缺少 Location"));
                response.body().close();
                uri = uri.resolve(location);
                continue;
            }
            if (status < 200 || status >= 300) {
                response.body().close();
                throw new IllegalStateException("HTTP " + status);
            }
            long declaredLength = response.headers().firstValueAsLong("content-length").orElse(-1);
            if (declaredLength > maxBytes) {
                response.body().close();
                throw new IllegalStateException("响应超过大小限制");
            }
            byte[] bytes = readLimited(response.body(), maxBytes);
            String contentType = response.headers().firstValue("content-type")
                    .orElse("application/octet-stream").split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            return new FetchedResource(uri, contentType, bytes);
        }
        throw new IllegalStateException("重定向次数过多");
    }

    private HttpResponse<InputStream> sendWithRetry(
            HttpRequest request, CrawlerSettingsService.NetworkSnapshot settings) throws Exception {
        IOException lastFailure = null;
        for (int attempt = 0; attempt <= settings.maxRetries(); attempt++) {
            try {
                HttpResponse<InputStream> response = send(request, settings);
                if (!isRetryableStatus(response.statusCode()) || attempt >= settings.maxRetries()) {
                    return response;
                }
                String retryAfter = response.headers().firstValue("retry-after").orElse(null);
                response.body().close();
                awaitRetry(attempt, settings.retryBaseDelayMillis(), retryAfter);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw exception;
            } catch (IOException exception) {
                lastFailure = exception;
                if (attempt >= settings.maxRetries()) throw exception;
                awaitRetry(attempt, settings.retryBaseDelayMillis(), null);
            }
        }
        throw lastFailure == null ? new IOException("请求重试失败") : lastFailure;
    }

    static boolean isRetryableStatus(int status) {
        return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
    }

    static long retryDelayMillis(int attempt, long baseDelayMillis, String retryAfter) {
        long multiplier = 1L << Math.min(Math.max(attempt, 0), 10);
        long exponential = Math.min(60_000L, Math.max(0, baseDelayMillis) * multiplier);
        long serverDelay = parseRetryAfterMillis(retryAfter);
        return Math.min(60_000L, Math.max(exponential, serverDelay));
    }

    private static long parseRetryAfterMillis(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Math.max(0, Long.parseLong(value.trim()) * 1000L);
        } catch (NumberFormatException ignored) {
            try {
                return Math.max(0, Duration.between(
                        ZonedDateTime.now(), ZonedDateTime.parse(
                                value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)).toMillis());
            } catch (Exception invalidDate) {
                return 0;
            }
        }
    }

    private void awaitRetry(int attempt, long baseDelayMillis, String retryAfter)
            throws InterruptedException {
        long delay = retryDelayMillis(attempt, baseDelayMillis, retryAfter);
        if (delay > 0) Thread.sleep(delay);
    }

    private HttpResponse<InputStream> send(
            HttpRequest request, CrawlerSettingsService.NetworkSnapshot settings) throws Exception {
        IOException lastFailure = null;
        for (CrawlerSettingsService.ProxyConfig proxy : settings.proxies()) {
            try {
                HttpResponse<InputStream> response = proxyClient(proxy, settings.connectTimeoutSeconds())
                        .send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (!isProxyFailureStatus(response.statusCode())) return response;
                response.body().close();
                lastFailure = new IOException(
                        "代理 " + proxy.host() + ":" + proxy.port() + " 返回 HTTP " + response.statusCode());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw exception;
            } catch (IOException exception) {
                lastFailure = exception;
            }
        }
        if (settings.directFallback()) {
            return directClient(settings.connectTimeoutSeconds())
                    .send(request, HttpResponse.BodyHandlers.ofInputStream());
        }
        if (lastFailure != null) throw new IOException("所有爬虫代理均连接失败", lastFailure);
        throw new IOException("未配置可用代理且已禁用直连");
    }

    private boolean isProxyFailureStatus(int status) {
        return status == 407 || status == 502 || status == 503 || status == 504;
    }

    private HttpClient directClient(int connectTimeoutSeconds) {
        return clients.computeIfAbsent("direct:" + connectTimeoutSeconds, ignored -> HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    private HttpClient proxyClient(
            CrawlerSettingsService.ProxyConfig proxy, int connectTimeoutSeconds) {
        String key = "proxy:" + proxy.id() + ":" + proxy.host() + ":" + proxy.port()
                + ":" + proxy.username() + ":" + java.util.Objects.hashCode(proxy.password())
                + ":" + connectTimeoutSeconds;
        return clients.computeIfAbsent(key, ignored -> {
            HttpClient.Builder builder = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .proxy(ProxySelector.of(new InetSocketAddress(proxy.host(), proxy.port())));
            if (proxy.username() != null && !proxy.username().isBlank()) {
                enableBasicProxyAuthentication();
                builder.authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        if (getRequestorType() != RequestorType.PROXY) return null;
                        return new PasswordAuthentication(
                                proxy.username(),
                                proxy.password() == null ? new char[0] : proxy.password().toCharArray());
                    }
                });
            }
            return builder.build();
        });
    }

    private void enableBasicProxyAuthentication() {
        if (System.getProperty("jdk.http.auth.tunneling.disabledSchemes") == null) {
            System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
        }
        if (System.getProperty("jdk.http.auth.proxying.disabledSchemes") == null) {
            System.setProperty("jdk.http.auth.proxying.disabledSchemes", "");
        }
    }

    public static String normalize(String value) {
        URI uri = URI.create(value).normalize();
        String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
            port = -1;
        }
        try {
            return new URI(scheme, null, host, port, uri.getPath(), uri.getQuery(), null).toString();
        } catch (Exception exception) {
            throw new IllegalArgumentException("无效 URL", exception);
        }
    }

    public static String urlHash(String normalizedUrl) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(normalizedUrl.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算 URL 哈希", exception);
        }
    }

    static void validate(URI uri, Set<String> allowedHosts) throws Exception {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) || host == null
                || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("只允许不含用户信息的 HTTP/HTTPS URL");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (!allowedHosts.contains(normalizedHost)) {
            throw new IllegalArgumentException("域名不在允许列表: " + normalizedHost);
        }
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) {
            throw new IllegalArgumentException("只允许标准 HTTP/HTTPS 端口");
        }
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (!isPublic(address)) {
                throw new IllegalArgumentException("目标解析到非公网地址");
            }
        }
    }

    private static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        if (address instanceof Inet4Address) {
            byte[] bytes = address.getAddress();
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return !(first == 0 || first == 127 || (first == 100 && second >= 64 && second <= 127)
                    || (first == 169 && second == 254) || first >= 224);
        }
        byte[] bytes = address.getAddress();
        return bytes.length != 16 || ((bytes[0] & 0xfe) != 0xfc);
    }

    private static Set<String> parseHosts(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .map(host -> host.toLowerCase(Locale.ROOT))
                .filter(host -> !host.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private byte[] readLimited(InputStream input, long maxBytes) throws Exception {
        try (input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IllegalStateException("响应超过大小限制");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private void awaitRateLimit(String host, long minRequestIntervalMillis) throws InterruptedException {
        long delay;
        synchronized (nextRequestAt) {
            long now = System.currentTimeMillis();
            long scheduled = Math.max(now, nextRequestAt.getOrDefault(host, 0L));
            delay = scheduled - now;
            nextRequestAt.put(host, scheduled + minRequestIntervalMillis);
        }
        if (delay > 0) Thread.sleep(delay);
    }

    public record FetchedResource(URI finalUri, String contentType, byte[] bytes) {}
}
