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
    private final com.aiphoto.repository.CrawlerNodeGroupRepository groupRepository = mock(com.aiphoto.repository.CrawlerNodeGroupRepository.class);
    private final com.aiphoto.repository.CrawlerExecutionLogRepository logRepository = mock(com.aiphoto.repository.CrawlerExecutionLogRepository.class);
    private final com.aiphoto.repository.CrawlJobRepository jobRepository = mock(com.aiphoto.repository.CrawlJobRepository.class);
    private final com.aiphoto.repository.CrawlSiteRepository siteRepository = mock(com.aiphoto.repository.CrawlSiteRepository.class);
    private final com.aiphoto.repository.CrawlerExecutorReceiptRepository receiptRepository = mock(com.aiphoto.repository.CrawlerExecutorReceiptRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private CrawlerExecutorService service;
    private CrawlerExecutor executor;
    private CrawlerProxy proxy;

    @BeforeEach void setup() throws Exception {
        service = new CrawlerExecutorService(repository, proxies, settings, secrets, mihomo, mapper,
                groupRepository, logRepository, jobRepository, siteRepository, receiptRepository);
        executor = new CrawlerExecutor();
        executor.setId(1L); executor.setName("Mihomo"); executor.setProxyMode("MIHOMO");
        executor.setProxyId(2L); executor.setProxyGroup("Crawler"); executor.setNodesJson("[\"A\",\"B\"]");
        executor.setSwitchMode("FAILOVER"); executor.setOptionsJson("{\"failureThreshold\":1}");
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

    @Test void siteQueueRunsOneJobPerAssignedExecutorAndRejectsDuplicateDispatch() {
        CrawlerExecutor first = new CrawlerExecutor(); first.setId(11L); first.setName("A");
        first.setProxyMode("DIRECT"); first.setOptionsJson("{\"siteId\":2}");
        CrawlerExecutor second = new CrawlerExecutor(); second.setId(12L); second.setName("B");
        second.setProxyMode("DIRECT"); second.setOptionsJson("{\"siteId\":2}");
        when(repository.findAllByOrderByIdAsc()).thenReturn(List.of(first, second));
        com.aiphoto.entity.CrawlJob job = new com.aiphoto.entity.CrawlJob(); job.setSiteId(2L);
        when(jobRepository.findById(anyLong())).thenReturn(Optional.of(job));
        try (var a = service.acquire(100L); var b = service.acquire(101L)) {
            assertThat(a).isNotNull(); assertThat(b).isNotNull();
            assertThat(service.acquire(100L)).isNull();
            assertThat(service.acquire(102L)).isNull();
        }
        try (var c = service.acquire(102L)) { assertThat(c).isNotNull(); }
    }

    @Test void assigningMultipleExecutorsPersistsSiteBinding() throws Exception {
        CrawlerExecutor other = new CrawlerExecutor(); other.setId(8L);
        when(repository.findAllByOrderByIdAsc()).thenReturn(List.of(executor, other));
        service.assignSiteExecutors(2L, List.of(1L, 8L));
        assertThat(mapper.readTree(executor.getOptionsJson()).path("siteId").asLong()).isEqualTo(2L);
        assertThat(mapper.readTree(other.getOptionsJson()).path("siteId").asLong()).isEqualTo(2L);
        verify(repository).saveAll(List.of(executor, other));
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
            assertThatThrownBy(session::network).hasMessageContaining("冻结");
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
    @Test void thresholdCountsOneExhaustedRequestAndSuccessResetsOnlyCurrentNode() throws Exception {
        executor.setOptionsJson("{\"failureThreshold\":2}");
        try (var session = service.acquire(10L)) {
            session.network(); session.failure(new IOException("failed"));
            assertThat(service.list().getFirst().cooldowns()).isEmpty();
            assertThat(service.list().getFirst().proxyStates().getFirst().failures()).isEqualTo(1);
            session.success();
            assertThat(service.list().getFirst().proxyStates().getFirst().failures()).isZero();
        }
    }

    @Test void manualSelectionDoesNotDependOnListOrder() throws Exception {
        executor.setSwitchMode("MANUAL"); executor.setOptionsJson("{\"manualNode\":\"B\"}");
        try (var session = service.acquire(10L)) {
            session.network();
            assertThat(service.list().getFirst().currentNode()).isEqualTo("B");
            verify(mihomo).select("http://localhost:9090", null, "Crawler", "B");
        }
    }

    @Test void disablingFailoverKeepsCurrentNodeOnRequestFailure() throws Exception {
        executor.setSwitchMode("TIME"); executor.setOptionsJson("{\"failover\":false,\"failureThreshold\":2}");
        try (var session = service.acquire(10L)) {
            session.network(); session.failure(new IOException("failed"));
            assertThat(session.autoFailover()).isFalse();
            session.network();
            assertThat(service.list().getFirst().currentNode()).isEqualTo("A");
        }
    }

    @Test void pageRotationCountsCompletedPagesAndDeduplicatesReceipts() throws Exception {
        executor.setSwitchMode("PAGE"); executor.setOptionsJson("{\"rotationPages\":1}");
        try (var session = service.acquire(10L)) {
            session.network(); session.pageCompleted(5L);
            assertThat(service.list().getFirst().completedPages()).isEqualTo(1);
            when(receiptRepository.existsByExecutorIdAndReceiptKey(1L, "page:10:5")).thenReturn(true);
            session.pageCompleted(5L);
            assertThat(service.list().getFirst().completedPages()).isEqualTo(1);
            session.network();
            assertThat(service.list().getFirst().currentNode()).isEqualTo("B");
            assertThat(service.list().getFirst().completedPages()).isZero();
        }
    }

    @Test void imageAndTaskRotationUseSuccessfulCompletionReceipts() throws Exception {
        executor.setSwitchMode("IMAGE"); executor.setOptionsJson("{\"rotationImages\":1,\"rotationTasks\":1}");
        try (var session = service.acquire(10L)) {
            session.network(); session.imageCompleted(8L); session.network();
            assertThat(service.list().getFirst().currentNode()).isEqualTo("B");
            executor.setSwitchMode("TASK"); session.taskCompleted("REVIEW"); session.network();
            assertThat(service.list().getFirst().currentNode()).isEqualTo("A");
            verify(receiptRepository).saveAndFlush(argThat(r -> r.getReceiptKey().equals("task:10:REVIEW")));
        }
    }

    @Test void timeRotationRestoresAccumulatedActiveTime() throws Exception {
        executor.setCurrentNode("A"); executor.setSwitchMode("TIME");
        executor.setOptionsJson("{\"rotationSeconds\":30}"); executor.setRuntimeJson("{\"activeMillis\":30000}");
        try (var session = service.acquire(10L)) {
            session.network(); assertThat(service.list().getFirst().currentNode()).isEqualTo("B");
            assertThat(service.list().getFirst().activeMillis()).isZero();
        }
    }

    @Test void randomRotationDoesNotImmediatelyRepeatCurrentNode() throws Exception {
        executor.setCurrentNode("A"); executor.setSwitchMode("REQUESTS"); executor.setRotateAfterRequests(1);
        executor.setCompletedRequests(1); executor.setOptionsJson("{\"selectionStrategy\":\"RANDOM\"}");
        try (var session = service.acquire(10L)) { session.network(); assertThat(service.list().getFirst().currentNode()).isEqualTo("B"); }
    }

    @Test void dedicatedFrozenSiteDoesNotFallBackToGlobalExecutor() {
        var job = new com.aiphoto.entity.CrawlJob(); job.setSiteId(3L);
        when(jobRepository.findById(10L)).thenReturn(Optional.of(job));
        executor.setOptionsJson("{\"siteId\":3,\"frozen\":true}");
        var global = new CrawlerExecutor(); global.setId(2L); global.setName("Global");
        when(repository.findAllByOrderByIdAsc()).thenReturn(List.of(executor, global));
        assertThat(service.acquire(10L)).isNull();
    }

    @Test void runtimeFreezePreservesWaitingJobAndUnfreezeAllowsAutomaticResume() throws Exception {
        var job = new com.aiphoto.entity.CrawlJob(); job.setId(10L); job.setStatus(com.aiphoto.entity.CrawlJob.Status.PAUSED);
        when(jobRepository.findById(10L)).thenReturn(Optional.of(job));
        when(repository.saveAndFlush(executor)).thenReturn(executor);
        try (var session = service.acquire(10L)) {
            service.freeze(1L, true);
            assertThatThrownBy(() -> session.begin("https://example.com"))
                    .isInstanceOf(CrawlerExecutorService.WaitingException.class);
            job.setErrorMessage("执行器等待：执行器已人工冻结");
            assertThat(service.list().getFirst().waitingJobId()).isEqualTo(10L);
        }
        service.resumeWaiting(); assertThat(job.getStatus()).isEqualTo(com.aiphoto.entity.CrawlJob.Status.PAUSED);
        service.freeze(1L, false); service.resumeWaiting();
        assertThat(job.getStatus()).isEqualTo(com.aiphoto.entity.CrawlJob.Status.QUEUED);
        assertThat(service.list().getFirst().waitingJobId()).isNull();
    }

    @Test void futureCooldownDoesNotResumeWaitingJobEarly() {
        executor.setRuntimeJson("{\"waitingJobId\":10,\"waitUntil\":\"2099-01-01T00:00:00Z\"}");
        service.resumeWaiting(); verify(jobRepository, never()).save(any());
    }

    @Test void ordinaryProxyCoolingIsIndependentAndPreservesConfiguredOrder() throws Exception {
        executor.setProxyMode("SELECTED"); proxy.setProxyType("HTTP");
        var secondary = new CrawlerProxy(); secondary.setId(3L); secondary.setName("Second"); secondary.setHost("other"); secondary.setPort(7890);
        when(proxies.findById(3L)).thenReturn(Optional.of(secondary));
        executor.setOptionsJson("{\"failureThreshold\":1,\"ordinaryProxies\":[{\"proxyId\":3,\"cooldownSeconds\":600},{\"proxyId\":2}]}");
        try (var session = service.acquire(10L)) {
            session.begin("https://example.com/");
            assertThat(session.network().proxies()).extracting(CrawlerSettingsService.ProxyConfig::id).containsExactly(3L, 2L);
            session.proxyFailed(3L, "connect failed"); session.finish(new IOException("failed"));
            session.begin("https://example.com/");
            assertThat(session.network().proxies()).extracting(CrawlerSettingsService.ProxyConfig::id).containsExactly(2L);
            assertThat(service.list().getFirst().proxyStates().getFirst().failures()).isEqualTo(1);
            session.finish(null);
        }
    }

    @Test void executionLogsSnapshotNodeAndKeepFailureEvenAfterFailoverSuccess() throws Exception {
        try (var session = service.acquire(10L)) {
            session.begin("https://example.com/image.jpg"); session.network(); session.proxyAttempt(2L);
            session.failure(new IOException("failed")); session.network(); session.proxyAttempt(2L); session.success(); session.finish(null);
            verify(logRepository).save(argThat(log -> log.getFailed() && "A".equals(log.getProxyNode())));
            verify(logRepository).save(argThat(log -> !log.getFailed() && "B".equals(log.getProxyNode()) && log.getAttempts() == 2));
        }
    }

    @Test void namedGroupUsesItsOwnHttpEntry() throws Exception {
        var group = new com.aiphoto.entity.CrawlerNodeGroup(); group.setId(9L); group.setProxyId(2L); group.setName("Dedicated");
        group.setControlGroup("Crawler"); group.setHost("dedicated-entry"); group.setPort(7899); group.setNodesJson("[\"A\",\"B\"]");
        when(groupRepository.findById(9L)).thenReturn(Optional.of(group));
        executor.setOptionsJson("{\"nodeGroupId\":9}");
        try (var session = service.acquire(10L)) {
            assertThat(session.network().proxies().getFirst().host()).isEqualTo("dedicated-entry");
            assertThat(session.network().proxies().getFirst().port()).isEqualTo(7899);
        }
    }

    @Test void referencedNodeGroupCannotBeDeleted() {
        var group = new com.aiphoto.entity.CrawlerNodeGroup(); group.setId(9L); group.setProxyId(2L);
        when(groupRepository.findById(9L)).thenReturn(Optional.of(group)); executor.setOptionsJson("{\"nodeGroupId\":9}");
        assertThatThrownBy(() -> service.deleteGroup(2L, 9L)).hasMessageContaining("引用");
    }

    @Test void hotMetadataUpdateDoesNotOverwriteNewSettingsOnRequestCompletion() throws Exception {
        when(repository.saveAndFlush(executor)).thenReturn(executor);
        try (var session = service.acquire(10L)) {
            session.begin("https://example.com/"); session.network();
            var options = new CrawlerExecutorOptions(); options.setDescription("Updated while running");
            service.save(1L, new CrawlerExecutorService.ExecutorRequest("Renamed", false, "MIHOMO", 2L, "Crawler", List.of("A","B"), "FAILOVER", 100, 300, options));
            session.success(); session.finish(null);
            assertThat(executor.getName()).isEqualTo("Renamed"); assertThat(executor.getEnabled()).isFalse();
            assertThat(executor.getOptionsJson()).contains("Updated while running");
        }
    }

    @Test void disabledFirstSelectedProxyDoesNotPreventUsingOtherSelectedProxies() throws Exception {
        executor.setProxyMode("SELECTED"); proxy.setProxyType("HTTP"); proxy.setEnabled(false);
        var other = new CrawlerProxy(); other.setId(3L); other.setName("Other"); other.setHost("other"); other.setPort(7890);
        when(proxies.findById(3L)).thenReturn(Optional.of(other));
        executor.setOptionsJson("{\"ordinaryProxies\":[{\"proxyId\":2},{\"proxyId\":3}]}");
        try (var session = service.acquire(10L)) {
            assertThat(session).isNotNull();
            assertThat(session.network().proxies()).extracting(CrawlerSettingsService.ProxyConfig::id).containsExactly(3L);
        }
    }

    @Test void referencedProxyCredentialsCanChangeButEndpointCannot() {
        when(repository.existsByProxyId(2L)).thenReturn(true);
        service.assertProxyUpdateAllowed(2L, new CrawlerSettingsService.ProxyRequest("Renamed", "localhost", 7895,
                null, "new-password", false, true, "MIHOMO", "http://localhost:9090", "new-token", false));
        assertThatThrownBy(() -> service.assertProxyUpdateAllowed(2L, new CrawlerSettingsService.ProxyRequest("Renamed", "other", 7895,
                null, "", false, true, "MIHOMO", "http://localhost:9090", "", false))).hasMessageContaining("引用");
    }

}
