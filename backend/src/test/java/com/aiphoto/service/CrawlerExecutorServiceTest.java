package com.aiphoto.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.aiphoto.entity.CrawlerExecutor;
import com.aiphoto.entity.CrawlerProxy;
import com.aiphoto.repository.CrawlerExecutorRepository;
import com.aiphoto.repository.CrawlerProxyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CrawlerExecutorServiceTest {
    private final CrawlerExecutorRepository repository = mock(CrawlerExecutorRepository.class);
    private final CrawlerProxyRepository proxies = mock(CrawlerProxyRepository.class);
    private final CrawlerSettingsService settings = mock(CrawlerSettingsService.class);
    private final CrawlerSecretService secrets = mock(CrawlerSecretService.class);
    private final MihomoApiClient mihomo = mock(MihomoApiClient.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private CrawlerExecutorService service;
    private CrawlerExecutor executor;
    private CrawlerProxy proxy;

    @BeforeEach void setup() throws Exception {
        service = new CrawlerExecutorService(repository, proxies, settings, secrets, mihomo, mapper);
        executor = new CrawlerExecutor();
        executor.setId(1L); executor.setName("Mihomo"); executor.setProxyMode("MIHOMO");
        executor.setProxyId(2L); executor.setProxyGroup("Crawler"); executor.setNodesJson("[\"A\",\"B\"]");
        executor.setSwitchMode("FAILOVER");
        proxy = new CrawlerProxy(); proxy.setId(2L); proxy.setProxyType("MIHOMO");
        proxy.setHost("localhost"); proxy.setPort(7895); proxy.setControllerUrl("http://localhost:9090");
        when(repository.findAllByOrderByIdAsc()).thenReturn(List.of(executor));
        when(repository.findById(1L)).thenReturn(Optional.of(executor));
        when(proxies.findById(2L)).thenReturn(Optional.of(proxy));
        when(settings.networkSnapshot()).thenReturn(new CrawlerSettingsService.NetworkSnapshot(
                true, 10, 30, 1000, 2, 1000, List.of()));
        when(mihomo.proxies(anyString(), isNull())).thenReturn(mapper.readTree(
                "{\"proxies\":{\"Crawler\":{\"type\":\"Selector\",\"all\":[\"A\",\"B\"],\"now\":\"A\"},\"A\":{\"type\":\"ss\"},\"B\":{\"type\":\"ss\"}}}"));
    }

    @Test void singleJobOwnsExecutorUntilRelease() {
        try (var first = service.acquire(10L)) {
            assertThat(first).isNotNull();
            assertThat(service.acquire(11L)).isNull();
            assertThat(service.list().getFirst().activeJobId()).isEqualTo(10L);
            assertThatThrownBy(() -> service.delete(1L)).hasMessageContaining("正在运行");
        }
        assertThat(service.current()).isNull();
        try (var second = service.acquire(11L)) { assertThat(second).isNotNull(); }
    }

    @Test void failuresCoolNodeAndSwitchWhilePreventingDirectFallback() throws Exception {
        try (var session = service.acquire(10L)) {
            assertThat(session.network().directFallback()).isFalse();
            assertThat(service.list().getFirst().currentNode()).isEqualTo("A");
            session.failure(new IOException("connection failed"));
            session.network();
            verify(mihomo).select("http://localhost:9090", null, "Crawler", "B");
            assertThat(service.list().getFirst().cooldowns()).containsKey("A");
            session.success();
            assertThat(executor.getCurrentNode()).isEqualTo("B");
            assertThat(executor.getCompletedRequests()).isEqualTo(1);
            assertThat(executor.getCooldownsJson()).contains("A");
        }
    }

    @Test void controllerErrorsDoNotFreezeNodes() throws Exception {
        when(mihomo.proxies(anyString(), isNull())).thenThrow(new IOException("unauthorized"));
        try (var session = service.acquire(10L)) {
            assertThatThrownBy(session::network).isInstanceOf(CrawlerExecutorService.ControlException.class);
            session.failure(new CrawlerExecutorService.ControlException("控制 API 不可用", null));
            assertThat(service.list().getFirst().cooldowns()).isEmpty();
            verify(mihomo, never()).select(anyString(), any(), anyString(), anyString());
        }
    }

    @Test void manualNodeDoesNotChangeDuringCooldown() throws Exception {
        executor.setSwitchMode("MANUAL");
        try (var session = service.acquire(10L)) {
            session.network(); session.failure(new IOException("failed"));
            assertThat(session.autoFailover()).isFalse();
            assertThatThrownBy(session::network).hasMessageContaining("冷却");
            verify(mihomo, never()).select(anyString(), any(), anyString(), eq("B"));
        }
    }

    @Test void rotatesAfterSuccessfulRequestThreshold() throws Exception {
        executor.setSwitchMode("REQUESTS"); executor.setRotateAfterRequests(1);
        try (var session = service.acquire(10L)) {
            session.network(); session.success(); session.network();
            assertThat(service.list().getFirst().currentNode()).isEqualTo("B");
            assertThat(service.list().getFirst().completedRequests()).isZero();
        }
    }

    @Test void restoresNodeCountersAndCooldownsAfterRestart() {
        executor.setCurrentNode("B"); executor.setCompletedRequests(7);
        executor.setCooldownsJson("{\"A\":\"2099-01-01T00:00:00Z\"}");
        assertThat(service.list().getFirst().currentNode()).isEqualTo("B");
        assertThat(service.list().getFirst().completedRequests()).isEqualTo(7);
        assertThat(service.list().getFirst().cooldowns()).containsKey("A");
    }

    @Test void disabledExecutorsDoNotDispatch() {
        executor.setEnabled(false);
        assertThat(service.acquire(10L)).isNull();
    }

    @Test void preventsSharedSelectorAndProxyDeletion() {
        assertThatThrownBy(() -> service.save(null, new CrawlerExecutorService.ExecutorRequest(
                "Duplicate", true, "MIHOMO", 2L, "Crawler", List.of("A"), "MANUAL", 100, 300)))
                .hasMessageContaining("只能绑定一个");
        when(repository.existsByProxyId(2L)).thenReturn(true);
        assertThatThrownBy(() -> service.assertProxyEditable(2L)).hasMessageContaining("引用");
    }

    @Test void rejectsProxyGroupsInNodeList() {
        assertThatThrownBy(() -> service.save(null, new CrawlerExecutorService.ExecutorRequest(
                "Invalid", true, "MIHOMO", 2L, "Crawler", List.of("Crawler"), "MANUAL", 100, 300)))
                .hasMessageContaining("具体代理节点");
    }
}
