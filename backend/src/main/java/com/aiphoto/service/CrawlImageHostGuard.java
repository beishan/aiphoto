package com.aiphoto.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.io.IOException;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

/** Lives for one download run: neither another task nor a manual retry inherits host failures. */
public final class CrawlImageHostGuard {
    public static final int CONSECUTIVE_TIMEOUTS = 3;
    private static final CircuitBreakerConfig CONFIG = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(CONSECUTIVE_TIMEOUTS)
            .minimumNumberOfCalls(CONSECUTIVE_TIMEOUTS)
            .failureRateThreshold(100)
            .slowCallDurationThreshold(Duration.ofDays(1))
            .waitDurationInOpenState(Duration.ofMinutes(2))
            .permittedNumberOfCallsInHalfOpenState(1)
            .ignoreExceptions(InterruptedException.class, CrawlerExecutorService.ControlException.class)
            .recordException(CrawlImageHostGuard::isTimeout)
            .build();
    private final Map<String, CircuitBreaker> hosts = new HashMap<>();

    public <T> T execute(String imageUrl, Callable<T> download) throws Exception {
        String origin = origin(imageUrl);
        CircuitBreaker breaker = hosts.computeIfAbsent(origin, key -> CircuitBreaker.of(key, CONFIG));
        try {
            return breaker.executeCallable(download);
        } catch (CallNotPermittedException cooling) {
            throw new HostCoolingException(origin);
        }
    }

    private static boolean isTimeout(Throwable failure) {
        java.util.Set<Throwable> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Throwable cause = failure; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException
                    || cause instanceof java.util.concurrent.TimeoutException) return true;
        }
        return false;
    }

    private static String origin(String url) {
        URI uri = URI.create(url);
        if (uri.getHost() == null || uri.getScheme() == null) {
            throw new IllegalArgumentException("图片地址无效");
        }
        int port = uri.getPort() >= 0 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        return uri.getScheme().toLowerCase(Locale.ROOT) + "://"
                + uri.getHost().toLowerCase(Locale.ROOT) + ":" + port;
    }

    public static final class HostCoolingException extends IOException {
        private HostCoolingException(String origin) {
            super("图床 " + origin + " 在本任务内连续 " + CONSECUTIVE_TIMEOUTS
                    + " 张图片超时，冷却期间本图床图片快速失败，避免重复等待；稍后可重试任务");
        }
    }
}
