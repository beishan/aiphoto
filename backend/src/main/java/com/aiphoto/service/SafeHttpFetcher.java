package com.aiphoto.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SafeHttpFetcher {

    private static final int MAX_REDIRECTS = 5;
    private static final String PAGE_ACCEPT = "text/html,image/*;q=0.9,*/*;q=0.1";
    private static final String IMAGE_ACCEPT = "image/jpeg,image/png,image/webp,image/gif,image/*;q=0.9,*/*;q=0.1";
    private static final ThreadLocal<ImageDownloadDeadline> IMAGE_DEADLINE = new ThreadLocal<>();
    private static final ThreadLocal<NodeSwitchBudget> IMAGE_NODE_BUDGET = new ThreadLocal<>();

    static final class NodeSwitchBudget {
        private final int limit;
        private String current;
        private int switches;
        NodeSwitchBudget(int limit) { this.limit = limit; }
        void use(String node) throws NodeSwitchLimitException {
            if (current != null && !current.equals(node)) {
                if (switches >= limit) throw new NodeSwitchLimitException(limit);
                switches++;
            }
            current = node;
        }
    }

    static final class NodeSwitchLimitException extends IOException {
        NodeSwitchLimitException(int limit) { super("单张图片节点切换已达到上限（" + limit + " 次）"); }
    }
    private static final ScheduledExecutorService BODY_TIMEOUTS = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "crawler-response-timeout");
        thread.setDaemon(true);
        return thread;
    });
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
        ImageDownloadDeadline deadline = new ImageDownloadDeadline(
                () -> crawlerSettingsService.networkSnapshot().imageDownloadTimeoutSeconds());
        IMAGE_DEADLINE.set(deadline);
        IMAGE_NODE_BUDGET.set(new NodeSwitchBudget(3));
        try {
            FetchedResource resource = fetchWithAccept(
                    url, blockedHosts, maxBytes, siteMinRequestIntervalMillis, IMAGE_ACCEPT);
            deadline.check();
            return resource;
        } catch (Exception exception) {
            throw imageFailure(exception);
        } finally {
            deadline.close();
            IMAGE_DEADLINE.remove();
            IMAGE_NODE_BUDGET.remove();
        }
    }

    private FetchedResource fetchWithAccept(
            String url, String blockedHosts, long maxBytes, long siteMinRequestIntervalMillis,
            String accept) throws Exception {
        CrawlerExecutorService.Session session = executorService.current();
        if (session == null) return fetchResource(url, blockedHosts, maxBytes, siteMinRequestIntervalMillis, null, accept);
        Exception failure = null;
        try {
            session.begin(url);
            if (IMAGE_ACCEPT.equals(accept)) IMAGE_NODE_BUDGET.set(new NodeSwitchBudget(session.maxImageNodeSwitches()));
            int attempts = session.autoFailover() ? session.nodeCount() : 1;
            boolean imageSwitchLimited = IMAGE_ACCEPT.equals(accept) && attempts > session.maxImageNodeSwitches() + 1;
            if (IMAGE_ACCEPT.equals(accept)) attempts = Math.min(attempts, session.maxImageNodeSwitches() + 1);
            for (int attempt = 0; attempt < attempts; attempt++) {
                managedClients.set(new java.util.ArrayList<>());
                try {
                    FetchedResource result = fetchResource(url, blockedHosts, maxBytes, siteMinRequestIntervalMillis, session, accept);
                    session.success();
                    return result;
                } catch (IOException exception) {
                    checkImageDeadline();
                    if (exception instanceof NodeSwitchLimitException) throw exception;
                    if (exception instanceof HttpStatusException status && !status.nodeFailure()) throw exception;
                    session.failure(exception);
                    if (!(exception instanceof CrawlerExecutorService.ControlException)
                            && imageSwitchLimited && attempt + 1 == attempts) {
                        NodeSwitchLimitException limit = new NodeSwitchLimitException(session.maxImageNodeSwitches());
                        limit.initCause(exception);
                        throw limit;
                    }
                    if (IMAGE_ACCEPT.equals(accept) && attempt + 1 == attempts) throw exception;
                    if (!(exception instanceof CrawlerExecutorService.ControlException)) session.waitIfUnavailable();
                    if (exception instanceof CrawlerExecutorService.ControlException || attempt + 1 == attempts) throw exception;
                } finally {
                    for (HttpClient client : managedClients.get()) client.shutdownNow();
                    managedClients.remove();
                }
            }
            throw new IOException("Mihomo 节点请求失败");
        } catch (Exception exception) {
            failure = imageFailure(exception);
            throw failure;
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
        int timeoutSeconds = IMAGE_ACCEPT.equals(accept)
                ? settings.imageDownloadTimeoutSeconds() : settings.requestTimeoutSeconds();
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            validate(uri, hosts);
            awaitRateLimit(uri.getHost(), requestIntervalMillis);
            checkImageDeadline();
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("User-Agent", "aiphoto-crawler/1.0")
                    .header("Accept", accept)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = sendWithRetry(request, settings, session, maxBytes, timeoutSeconds);
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("location")
                        .orElseThrow(() -> new IllegalStateException("重定向缺少 Location"));
                uri = uri.resolve(location);
                continue;
            }
            if (status < 200 || status >= 300) {
                throw new HttpStatusException(status);
            }
            long declaredLength = response.headers().firstValueAsLong("content-length").orElse(-1);
            if (declaredLength > maxBytes) {
                throw new IllegalStateException("响应超过大小限制");
            }
            String contentType = response.headers().firstValue("content-type")
                    .orElse("application/octet-stream").split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            ImageDownloadDeadline imageDeadline = IMAGE_DEADLINE.get();
            if (imageDeadline != null) {
                imageDeadline.check();
                imageDeadline.close();
            }
            return new FetchedResource(uri, contentType, response.body());
        }
        throw new IllegalStateException("重定向次数过多");
    }

    private HttpResponse<byte[]> sendWithRetry(
            HttpRequest request, CrawlerSettingsService.NetworkSnapshot settings,
            CrawlerExecutorService.Session session, long maxBytes, int timeoutSeconds) throws Exception {
        IOException lastFailure = null;
        int retries = session != null && session.managedMihomo() ? Math.max(1, settings.maxRetries()) : settings.maxRetries();
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                if (attempt > 0) awaitRateLimit(request.uri().getHost(), settings.minRequestIntervalMillis());
                HttpResponse<byte[]> response = send(request, settings, session, maxBytes, timeoutSeconds);
                if (!(isRetryableStatus(response.statusCode()) || (session != null && session.managedMihomo()
                        && (response.statusCode() == 401 || response.statusCode() == 403 || response.statusCode() == 451))) || attempt >= retries) {
                    return response;
                }
                String retryAfter = response.headers().firstValue("retry-after").orElse(null);
                awaitRetry(attempt, settings.retryBaseDelayMillis(), retryAfter);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw exception;
            } catch (IOException exception) {
                checkImageDeadline();
                if (exception instanceof NodeSwitchLimitException) throw exception;
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

    private HttpResponse<byte[]> send(
            HttpRequest request, CrawlerSettingsService.NetworkSnapshot settings,
            CrawlerExecutorService.Session session, long maxBytes, int timeoutSeconds) throws Exception {
        IOException lastFailure = null;
        for (CrawlerSettingsService.ProxyConfig proxy : settings.proxies()) {
            if (session != null && !session.managedMihomo() && !session.ordinaryAvailable(proxy.id())) continue;
            NodeSwitchBudget budget = IMAGE_NODE_BUDGET.get();
            if (budget != null && (session == null || !session.managedMihomo())) budget.use("proxy:" + proxy.id());
            try {
                if (session != null) session.proxyAttempt(proxy.id());
                HttpResponse<byte[]> response = sendWithBodyTimeout(
                        proxyClient(proxy, settings.connectTimeoutSeconds(), session), request,
                        maxBytes, timeoutSeconds);
                if (!isProxyFailureStatus(response.statusCode())) {
                    if (session != null && !session.managedMihomo()) {
                        if (response.statusCode() < 400) session.proxySucceeded(proxy.id());
                        else if (new HttpStatusException(response.statusCode()).nodeFailure()) session.proxyFailed(proxy.id(), "HTTP " + response.statusCode());
                    }
                    return response;
                }
                if (session != null && !session.managedMihomo()) session.proxyFailed(proxy.id(), "代理返回 HTTP " + response.statusCode());
                lastFailure = new IOException(
                        "代理 " + proxy.host() + ":" + proxy.port() + " 返回 HTTP " + response.statusCode());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw exception;
            } catch (IOException exception) {
                checkImageDeadline();
                lastFailure = exception;
                if (session != null && !session.managedMihomo()) session.proxyFailed(proxy.id(), "代理连接失败");
            }
        }
        if (settings.directFallback()) {
            NodeSwitchBudget budget = IMAGE_NODE_BUDGET.get();
            if (budget != null) budget.use("direct");
            if (session != null) session.proxyAttempt(null);
            return sendWithBodyTimeout(directClient(settings.connectTimeoutSeconds()), request,
                    maxBytes, timeoutSeconds);
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

    static HttpResponse<byte[]> sendWithBodyTimeout(
            HttpClient client, HttpRequest request, long maxBytes, int timeoutSeconds) throws Exception {
        ImageDownloadDeadline imageDeadline = IMAGE_DEADLINE.get();
        if (imageDeadline != null) {
            imageDeadline.start();
            imageDeadline.check();
        }
        AtomicBoolean timedOut = new AtomicBoolean();
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        try {
            return client.send(request,
                    limitedBodyHandler(maxBytes, timeoutSeconds, deadlineNanos, timedOut));
        } catch (IOException exception) {
            if (timedOut.get()) {
                throw new java.net.http.HttpTimeoutException(
                        "响应体下载超过 " + timeoutSeconds + " 秒");
            }
            throw exception;
        }
    }

    private static void checkImageDeadline() throws java.net.http.HttpTimeoutException {
        ImageDownloadDeadline deadline = IMAGE_DEADLINE.get();
        if (deadline != null) deadline.check();
    }

    private static Exception imageFailure(Exception exception) {
        ImageDownloadDeadline deadline = IMAGE_DEADLINE.get();
        if (deadline != null) {
            try { deadline.check(); }
            catch (java.net.http.HttpTimeoutException timeout) {
                deadline.clearTimeoutInterrupt();
                return timeout;
            }
        }
        return exception;
    }

    static HttpResponse.BodyHandler<byte[]> limitedBodyHandler(long maxBytes, int timeoutSeconds) {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        return limitedBodyHandler(maxBytes, timeoutSeconds, deadlineNanos, new AtomicBoolean());
    }

    private static HttpResponse.BodyHandler<byte[]> limitedBodyHandler(
            long maxBytes, int timeoutSeconds, long deadlineNanos, AtomicBoolean timedOut) {
        return responseInfo -> new HttpResponse.BodySubscriber<>() {
            private final CompletableFuture<byte[]> body = new CompletableFuture<>();
            private final ByteArrayOutputStream output = new ByteArrayOutputStream();
            private Flow.Subscription subscription;
            private long total;
            private ScheduledFuture<?> timeout;

            @Override
            public CompletionStage<byte[]> getBody() {
                return body;
            }

            @Override
            public void onSubscribe(Flow.Subscription value) {
                subscription = value;
                timeout = BODY_TIMEOUTS.schedule(() -> {
                    timedOut.set(true);
                    value.cancel();
                    body.completeExceptionally(new java.net.http.HttpTimeoutException(
                            "响应体下载超过 " + timeoutSeconds + " 秒"));
                }, Math.max(0, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS);
                value.request(1);
            }

            @Override
            public void onNext(java.util.List<ByteBuffer> buffers) {
                for (ByteBuffer buffer : buffers) {
                    int remaining = buffer.remaining();
                    if (total + remaining > maxBytes) {
                        subscription.cancel();
                        timeout.cancel(false);
                        body.completeExceptionally(new IllegalStateException("响应超过大小限制"));
                        return;
                    }
                    byte[] chunk = new byte[remaining];
                    buffer.get(chunk);
                    output.write(chunk, 0, chunk.length);
                    total += remaining;
                }
                subscription.request(1);
            }

            @Override
            public void onError(Throwable error) {
                if (timeout != null) timeout.cancel(false);
                body.completeExceptionally(error);
            }

            @Override
            public void onComplete() {
                if (timeout != null) timeout.cancel(false);
                body.complete(output.toByteArray());
            }
        };
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
