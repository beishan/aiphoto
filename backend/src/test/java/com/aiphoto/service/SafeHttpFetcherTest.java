package com.aiphoto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SafeHttpFetcherTest {

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

    @Test
    void rejectsLoopbackTargetsEvenWhenHostIsAllowed() {
        assertThatThrownBy(() -> SafeHttpFetcher.validate(
                URI.create("http://127.0.0.1/image"), Set.of("127.0.0.1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非公网地址");
    }

    @Test
    void rejectsPrivateIpv6Targets() {
        assertThatThrownBy(() -> SafeHttpFetcher.validate(
                URI.create("http://[fd00::1]/image"), Set.of("[fd00::1]")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCredentialsAndNonStandardPorts() {
        assertThatThrownBy(() -> SafeHttpFetcher.validate(
                URI.create("https://user:pass@example.com/"), Set.of("example.com")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SafeHttpFetcher.validate(
                URI.create("https://example.com:8443/"), Set.of("example.com")))
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
}
