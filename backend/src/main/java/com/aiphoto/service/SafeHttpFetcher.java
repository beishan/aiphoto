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
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SafeHttpFetcher {

    private static final int MAX_REDIRECTS = 5;
    private static final String PAGE_ACCEPT = "text/html,image/*;q=0.9,*/*;q=0.1";
    private static final String IMAGE_ACCEPT = "image/jpeg,image/png,image/webp,image/gif,image/*;q=0.9,*/*;q=0.1";
    private final CrawlerSettingsService crawlerSettingsService;
    private final CrawlerExecutorService executorService;
    private final ThreadLocal<java.util.List<HttpClient>> managedClients = new ThreadLocal<>();
    private final ConcurrentHashMap<String, Long> nextRequestAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, HttpClient> clients = new ConcurrentHashMap<>();

    public String activeProxyName(Long jobId) {
        return executorService.activeProxyName(jobId);
    }

    public FetchedResource fetch(String url, String blockedHosts, long maxBytes) throws Exception {
        return fetch(url, blockedHosts, maxBytes, 0L);
    }

    public FetchedResource fetch(
            String url, String blockedHosts, long maxBytes, long siteMinRequestIntervalMillis)
            throws Exception {
        return fetchWithAccept(url, blockedHosts, maxBytes, siteMinRequestIntervalMillis, PAGE_ACCEPT);
    }

    /** Prefer image bytes: some hosts redirect HTML-preferring requests to a viewer page. */
    public FetchedResource fetchImage(
            String url, String blockedHosts, long maxBytes, long siteMinRequestIntervalMillis)
            throws Exception {
        return fetchWithAccept(url, blockedHosts, maxBytes, siteMinRequestIntervalMillis, IMAGE_ACCEPT);
    }

    private FetchedResource fetchWithAccept(
            String url, String blockedHosts, long maxBytes, long siteMinRequestIntervalMillis,
            String accept) throws Exception {
        CrawlerExecutorService.Session session = executorService.current();
        if (session == null) return fetchResource(url, blockedHosts, maxBytes, siteMinRequestIntervalMillis, null, accept);
        Exception failure = null;
        try {
            session.begin(url);
            int attempts = session.autoFailover() ? session.nodeCount() : 1;
            for (int attempt = 0; attempt < attempts; attempt++) {
                managedClients.set(new java.util.ArrayList<>());
                try {
                    FetchedResource result = fetchResource(url, blockedHosts, maxBytes, siteMinRequestIntervalMillis, session, accept);
                    session.success();
                    return result;
                } catch (IOException exception) {
                    if (exception instanceof HttpStatusException status && !status.nodeFailure()) throw exception;
                    session.failure(exception);
                    if (!(exception instanceof CrawlerExecutorService.ControlException)) session.waitIfUnavailable();
                    if (exception instanceof CrawlerExecutorService.ControlException || attempt + 1 == attempts) throw exception;
                } finally {
                    for (HttpClient client : managedClients.get()) client.close();
                    managedClients.remove();
                }
            }
            throw new IOException("Mihomo 节点请求失败");
        } catch (Exception exception) {
            failure = exception;
            throw exception;
        } finally {
            session.finish(failure);
        }
    }

    public FetchedResource fetchForInspection(
            String url, String blockedHosts, long maxBytes, long siteMinRequestIntervalMillis,
            CrawlerSettingsService.NetworkSnapshot settings) throws Exception {
        return fetchResource(url, blockedHosts, maxBytes, siteMinRequestIntervalMillis, null, settings, PAGE_ACCEPT);
    }

    private FetchedResource fetchResource(String url, String blockedHosts, long maxBytes,
            long siteMinRequestIntervalMillis, CrawlerExecutorService.Session session, String accept) throws Exception {
        return fetchResource(url, blockedHosts, maxBytes, siteMinRequestIntervalMillis, session,
                session == null ? crawlerSettingsService.networkSnapshot() : session.network(), accept);
    }

    private FetchedResource fetchResource(String url, String blockedHosts, long maxBytes,
            long siteMinRequestIntervalMillis, CrawlerExecutorService.Session session,
            CrawlerSettingsService.NetworkSnapshot settings, String accept) throws Exception {
        Set<String> hosts = CrawlHostPolicy.parse(blockedHosts);
        URI uri = URI.create(url);
        validate(uri, hosts);
        long requestIntervalMillis = Math.max(
                settings.minRequestIntervalMillis(), siteMinRequestIntervalMillis);
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            validate(uri, hosts);
            awaitRateLimit(uri.getHost(), requestIntervalMillis);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(settings.requestTimeoutSeconds()))
                    .header("User-Agent", "aiphoto-crawler/1.0")
                    .header("Accept", accept)
                    .GET()
                    .build();
            HttpResponse<InputStream> response = sendWithRetry(request, settings, session);
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
                throw new HttpStatusException(status);
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
            HttpRequest request, CrawlerSettingsService.NetworkSnapshot settings,
            CrawlerExecutorService.Session session) throws Exception {
        IOException lastFailure = null;
        int retries = session != null && session.managedMihomo() ? Math.max(1, settings.maxRetries()) : settings.maxRetries();
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                if (attempt > 0) awaitRateLimit(request.uri().getHost(), settings.minRequestIntervalMillis());
                HttpResponse<InputStream> response = send(request, settings, session);
                if (!(isRetryableStatus(response.statusCode()) || (session != null && session.managedMihomo()
                        && (response.statusCode() == 401 || response.statusCode() == 403 || response.statusCode() == 451))) || attempt >= retries) {
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
                if (attempt >= retries) throw exception;
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
            HttpRequest request, CrawlerSettingsService.NetworkSnapshot settings,
            CrawlerExecutorService.Session session) throws Exception {
        IOException lastFailure = null;
        for (CrawlerSettingsService.ProxyConfig proxy : settings.proxies()) {
            if (session != null && !session.managedMihomo() && !session.ordinaryAvailable(proxy.id())) continue;
            try {
                if (session != null) session.proxyAttempt(proxy.id());
                HttpResponse<InputStream> response = proxyClient(proxy, settings.connectTimeoutSeconds(), session)
                        .send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (!isProxyFailureStatus(response.statusCode())) {
                    if (session != null && !session.managedMihomo()) {
                        if (response.statusCode() < 400) session.proxySucceeded(proxy.id());
                        else if (new HttpStatusException(response.statusCode()).nodeFailure()) session.proxyFailed(proxy.id(), "HTTP " + response.statusCode());
                    }
                    return response;
                }
                if (session != null && !session.managedMihomo()) session.proxyFailed(proxy.id(), "代理返回 HTTP " + response.statusCode());
                response.body().close();
                lastFailure = new IOException(
                        "代理 " + proxy.host() + ":" + proxy.port() + " 返回 HTTP " + response.statusCode());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw exception;
            } catch (IOException exception) {
                lastFailure = exception;
                if (session != null && !session.managedMihomo()) session.proxyFailed(proxy.id(), "代理连接失败");
            }
        }
        if (settings.directFallback()) {
            if (session != null) session.proxyAttempt(null);
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
                .proxy(new ProxySelector() {
                    public java.util.List<java.net.Proxy> select(URI uri) { return java.util.List.of(java.net.Proxy.NO_PROXY); }
                    public void connectFailed(URI uri, java.net.SocketAddress address, IOException error) { }
                })
                .build());
    }

    private HttpClient proxyClient(
            CrawlerSettingsService.ProxyConfig proxy, int connectTimeoutSeconds,
            CrawlerExecutorService.Session session) {
        String key = "proxy:" + proxy.id() + ":" + proxy.host() + ":" + proxy.port()
                + ":" + proxy.username() + ":" + java.util.Objects.hashCode(proxy.password())
                + ":" + connectTimeoutSeconds;
        java.util.function.Supplier<HttpClient> factory = () -> {
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
        };
        if (session != null && session.managedMihomo()) {
            HttpClient client = factory.get();
            managedClients.get().add(client);
            return client;
        }
        return clients.computeIfAbsent(key, ignored -> factory.get());
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

    static void validate(URI uri, Set<String> blockedHosts) throws Exception {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) || host == null
                || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("只允许不含用户信息的 HTTP/HTTPS URL");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (CrawlHostPolicy.isBlocked(normalizedHost, blockedHosts)) {
            throw new IllegalArgumentException("域名在禁止访问列表中：" + normalizedHost);
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

    static class HttpStatusException extends IOException {
        private final int status;
        HttpStatusException(int status) { super("HTTP " + status); this.status = status; }
        boolean nodeFailure() { return status == 401 || status == 403 || status == 429 || status == 451 || status >= 500; }
    }

    public record FetchedResource(URI finalUri, String contentType, byte[] bytes) {}
}
