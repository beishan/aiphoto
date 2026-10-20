package com.aiphoto.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Map;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CrawlImageHostGuardTest {
    private static final String HOST = "https://www.qpic.ws/images/2025/03/27/";

    @Test void threeDifferentImageTimeoutsStopSubsequentRequestsToTheSameHost() throws Exception {
        var guard = new CrawlImageHostGuard();
        timeout(guard, HOST + "a.jpg");
        timeout(guard, HOST + "b.jpg");
        timeout(guard, HOST + "c.jpg");
        @SuppressWarnings("unchecked") Callable<String> request = mock(Callable.class);
        assertThatThrownBy(() -> guard.execute(HOST + "d.jpg", request))
                .isInstanceOf(CrawlImageHostGuard.HostCoolingException.class)
                .hasMessageContaining("连续 3").hasMessageContaining("重试");
        verifyNoInteractions(request);
    }

    @Test void otherHostsAndFreshRunsDoNotInheritTheCircuit() throws Exception {
        var guard = open();
        assertThat(guard.execute("https://healthy.example/a.jpg", () -> "healthy")).isEqualTo("healthy");
        assertThat(new CrawlImageHostGuard().execute(HOST + "a.jpg", () -> "retry"))
                .isEqualTo("retry");
    }

    @Test void successfulImageBreaksTheConsecutiveTimeoutSequence() throws Exception {
        var guard = new CrawlImageHostGuard();
        timeout(guard, HOST + "a.jpg");
        timeout(guard, HOST + "b.jpg");
        guard.execute(HOST + "ok.jpg", () -> "ok");
        timeout(guard, HOST + "c.jpg");
        timeout(guard, HOST + "d.jpg");
        assertThat(guard.execute(HOST + "e.jpg", () -> "ok")).isEqualTo("ok");
    }

    @Test void ordinaryErrorsDoNotTripTheHostOrCountAsNetworkTimeouts() throws Exception {
        var guard = new CrawlImageHostGuard();
        timeout(guard, HOST + "a.jpg");
        timeout(guard, HOST + "b.jpg");
        assertThatThrownBy(() -> guard.execute(HOST + "missing.jpg", () -> {
            throw new IOException("HTTP 404");
        })).isInstanceOf(IOException.class).isNotInstanceOf(CrawlImageHostGuard.HostCoolingException.class);
        timeout(guard, HOST + "c.jpg");
        timeout(guard, HOST + "d.jpg");
        assertThat(guard.execute(HOST + "ok.jpg", () -> "ok")).isEqualTo("ok");
    }

    @Test void wrappedTimeoutsAreRecognizedAcrossNodeAndProxyFailures() {
        var guard = new CrawlImageHostGuard();
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> guard.execute(HOST + "bad.jpg", () -> {
                throw new IOException("所有代理失败", new SocketTimeoutException("timed out"));
            })).isInstanceOf(IOException.class);
        }
        assertThatThrownBy(() -> guard.execute(HOST + "next.jpg", () -> "unexpected"))
                .isInstanceOf(CrawlImageHostGuard.HostCoolingException.class);
    }

    @Test void interruptAndExecutorControlNeverCondemnAnImageHost() throws Exception {
        var guard = new CrawlImageHostGuard();
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> guard.execute(HOST + "a.jpg", () -> {
                throw new CrawlerExecutorService.ControlException("暂停", new HttpTimeoutException("API timeout"));
            })).isInstanceOf(CrawlerExecutorService.ControlException.class);
            assertThatThrownBy(() -> guard.execute(HOST + "b.jpg", () -> {
                throw new InterruptedException("暂停");
            })).isInstanceOf(InterruptedException.class);
        }
        assertThat(guard.execute(HOST + "ok.jpg", () -> "ok")).isEqualTo("ok");
    }

    @Test void hostCaseAndExplicitDefaultPortShareOneCircuit() {
        var guard = new CrawlImageHostGuard();
        timeout(guard, "https://WWW.QPIC.WS/a.jpg");
        timeout(guard, "https://www.qpic.ws:443/b.jpg");
        timeout(guard, "https://www.qpic.ws/c.jpg");
        assertThatThrownBy(() -> guard.execute(HOST + "d.jpg", () -> "unexpected"))
                .isInstanceOf(CrawlImageHostGuard.HostCoolingException.class);
    }

    @Test @SuppressWarnings("unchecked")
    void recoveryProbeReopensNormalDownloadsAfterCooldown() throws Exception {
        var guard = open();
        var hosts = (Map<String, CircuitBreaker>) ReflectionTestUtils.getField(guard, "hosts");
        var breaker = hosts.get("https://www.qpic.ws:443");
        assertThat(breaker.getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState().apply(1))
                .isEqualTo(120000L);
        breaker.transitionToHalfOpenState();
        assertThat(guard.execute(HOST + "probe.jpg", () -> "ok")).isEqualTo("ok");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(guard.execute(HOST + "next.jpg", () -> "next")).isEqualTo("next");
    }

    private CrawlImageHostGuard open() {
        var guard = new CrawlImageHostGuard();
        for (int i = 0; i < 3; i++) timeout(guard, HOST + i + ".jpg");
        return guard;
    }

    private void timeout(CrawlImageHostGuard guard, String url) {
        assertThatThrownBy(() -> guard.execute(url, () -> {
            throw new HttpTimeoutException("图片下载超时");
        })).isInstanceOf(HttpTimeoutException.class);
    }
}
