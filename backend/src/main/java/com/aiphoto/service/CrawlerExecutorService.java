package com.aiphoto.service;

import com.aiphoto.entity.CrawlerExecutor;
import com.aiphoto.entity.CrawlerProxy;
import com.aiphoto.repository.CrawlerExecutorRepository;
import com.aiphoto.repository.CrawlerProxyRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Single backend instance: one job per executor and one executor per Mihomo selector. */
@Service
@RequiredArgsConstructor
public class CrawlerExecutorService {
    private final CrawlerExecutorRepository repository;
    private final CrawlerProxyRepository proxyRepository;
    private final CrawlerSettingsService settingsService;
    private final CrawlerSecretService secrets;
    private final MihomoApiClient mihomo;
    private final ObjectMapper mapper;
    private final Map<Long, RuntimeState> states = new HashMap<>();
    private final ThreadLocal<Session> current = new ThreadLocal<>();

    public synchronized List<ExecutorView> list() {
        return repository.findAllByOrderByIdAsc().stream().map(this::view).toList();
    }

    @Transactional
    public synchronized ExecutorView save(Long id, ExecutorRequest request) throws Exception {
        if (id == null && repository.count() >= 16) throw new IllegalArgumentException("最多配置 16 个执行器");
        if (id != null && active(id)) throw new IllegalArgumentException("执行器正在运行，请等待任务结束后修改");
        if (request.name() == null || request.name().isBlank() || request.name().length() > 100) {
            throw new IllegalArgumentException("执行器名称不能为空且不能超过 100 字");
        }
        if (request.proxyMode() == null || !List.of("DEFAULT", "DIRECT", "SELECTED", "MIHOMO").contains(request.proxyMode())) {
            throw new IllegalArgumentException("无效代理模式");
        }
        if (request.switchMode() == null || !List.of("MANUAL", "FAILOVER", "REQUESTS").contains(request.switchMode())) {
            throw new IllegalArgumentException("无效节点切换方式");
        }
        if (request.rotateAfterRequests() < 1 || request.rotateAfterRequests() > 100000
                || request.cooldownSeconds() < 1 || request.cooldownSeconds() > 86400) {
            throw new IllegalArgumentException("轮换次数或冷却时间超出范围");
        }
        List<String> nodes = request.nodes() == null ? List.of() : request.nodes().stream()
                .filter(Objects::nonNull).map(String::trim).filter(n -> !n.isEmpty()).distinct().toList();
        if (nodes.size() > 256 || nodes.stream().anyMatch(node -> node.length() > 255)
                || (request.proxyGroup() != null && request.proxyGroup().length() > 255)) {
            throw new IllegalArgumentException("节点列表或受控组名称过长");
        }
        if ("SELECTED".equals(request.proxyMode()) || "MIHOMO".equals(request.proxyMode())) {
            CrawlerProxy proxy = requireProxy(request.proxyId());
            if (!proxy.getEnabled()) throw new IllegalArgumentException("代理已停用");
            if ("MIHOMO".equals(request.proxyMode())) {
                if (!"MIHOMO".equals(proxy.getProxyType())) throw new IllegalArgumentException("请选择 Mihomo 代理");
                if (nodes.isEmpty() || ("REQUESTS".equals(request.switchMode()) && nodes.size() < 2)) {
                    throw new IllegalArgumentException("请选择节点，按请求轮换至少需要两个节点");
                }
                JsonNode all = catalog(proxy).path("proxies");
                JsonNode group = all.path(request.proxyGroup() == null ? "" : request.proxyGroup());
                if (!"Selector".equals(group.path("type").asText())) throw new IllegalArgumentException("受控组必须是 Selector");
                Set<String> members = new HashSet<>();
                group.path("all").forEach(n -> members.add(n.asText()));
                for (String node : nodes) {
                    JsonNode item = all.path(node);
                    if (!members.contains(node) || item.isMissingNode() || item.has("all")
                            || List.of("DIRECT", "REJECT", "REJECT-DROP", "PASS", "COMPATIBLE").contains(item.path("type").asText().toUpperCase(Locale.ROOT))) {
                        throw new IllegalArgumentException("备用列表只能包含受控组中的具体代理节点: " + node);
                    }
                }
                for (CrawlerExecutor other : repository.findAllByOrderByIdAsc()) {
                    if (Objects.equals(other.getId(), id) || !"MIHOMO".equals(other.getProxyMode())) continue;
                    CrawlerProxy otherProxy = requireProxy(other.getProxyId());
                    if (Objects.equals(proxy.getControllerUrl(), otherProxy.getControllerUrl())
                            && Objects.equals(request.proxyGroup(), other.getProxyGroup())) {
                        throw new IllegalArgumentException("同一 Mihomo 受控组只能绑定一个执行器");
                    }
                }
            } else if (!"HTTP".equals(proxy.getProxyType())) {
                throw new IllegalArgumentException("普通指定代理模式只能选择普通代理");
            }
        }
        CrawlerExecutor executor = id == null ? new CrawlerExecutor() : repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("执行器不存在"));
        executor.setName(request.name().trim());
        executor.setEnabled(request.enabled());
        executor.setProxyMode(request.proxyMode());
        executor.setProxyId(List.of("SELECTED", "MIHOMO").contains(request.proxyMode()) ? request.proxyId() : null);
        executor.setProxyGroup("MIHOMO".equals(request.proxyMode()) ? request.proxyGroup() : null);
        executor.setNodesJson(mapper.writeValueAsString(nodes));
        executor.setSwitchMode(request.switchMode());
        executor.setRotateAfterRequests(request.rotateAfterRequests());
        executor.setCooldownSeconds(request.cooldownSeconds());
        executor.setCurrentNode(null);
        executor.setCompletedRequests(0);
        executor.setCooldownsJson("{}");
        executor.setLastError(null);
        executor = repository.saveAndFlush(executor);
        states.remove(executor.getId());
        return view(executor);
    }

    @Transactional
    public synchronized void delete(Long id) {
        if (active(id)) throw new IllegalArgumentException("执行器正在运行，无法删除");
        if (!repository.existsById(id)) throw new IllegalArgumentException("执行器不存在");
        repository.deleteById(id);
        states.remove(id);
    }

    public synchronized void assertProxyEditable(Long id) {
        if (repository.existsByProxyId(id)) throw new IllegalArgumentException("代理已被执行器引用，请先修改执行器解除引用");
    }

    public JsonNode nodes(Long proxyId) throws Exception { return catalog(requireMihomo(proxyId)); }
    public int delay(Long proxyId, String node) throws Exception {
        CrawlerProxy proxy = requireMihomo(proxyId);
        return mihomo.delay(proxy.getControllerUrl(), secrets.decrypt(proxy.getSecretCiphertext()), node);
    }

    public synchronized Session acquire(Long jobId) {
        List<CrawlerExecutor> executors = repository.findAllByOrderByIdAsc();
        // An implicit default executor preserves existing deployments until explicit executors are added.
        if (executors.isEmpty()) {
            if (active(0L)) return null;
            RuntimeState state = states.computeIfAbsent(0L, ignored -> new RuntimeState());
            state.jobId = jobId;
            Session session = new Session(null, state);
            current.set(session);
            return session;
        }
        for (CrawlerExecutor executor : executors) {
            if (!executor.getEnabled() || active(executor.getId())) continue;
            if (executor.getProxyId() != null) {
                CrawlerProxy proxy = proxyRepository.findById(executor.getProxyId()).orElse(null);
                if (proxy == null || !proxy.getEnabled() || !Objects.equals(proxy.getProxyType(),
                        "MIHOMO".equals(executor.getProxyMode()) ? "MIHOMO" : "HTTP")) continue;
            }
            RuntimeState state = states.computeIfAbsent(executor.getId(), ignored -> restore(executor));
            state.jobId = jobId;
            Session session = new Session(executor, state);
            current.set(session);
            return session;
        }
        return null;
    }

    public Session current() { return current.get(); }
    private boolean active(Long id) { return states.containsKey(id) && states.get(id).jobId != null; }
    private CrawlerProxy requireProxy(Long id) {
        if (id == null) throw new IllegalArgumentException("请选择代理");
        return proxyRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("代理不存在"));
    }
    private CrawlerProxy requireMihomo(Long id) {
        CrawlerProxy proxy = requireProxy(id);
        if (!"MIHOMO".equals(proxy.getProxyType())) throw new IllegalArgumentException("代理不是 Mihomo 类型");
        return proxy;
    }
    private JsonNode catalog(CrawlerProxy proxy) throws Exception {
        return mihomo.proxies(proxy.getControllerUrl(), secrets.decrypt(proxy.getSecretCiphertext()));
    }
    private List<String> nodes(CrawlerExecutor executor) {
        try { return mapper.readValue(executor.getNodesJson(), new TypeReference<List<String>>() {}); }
        catch (Exception exception) { throw new IllegalStateException("执行器节点配置损坏"); }
    }
    private RuntimeState restore(CrawlerExecutor executor) {
        RuntimeState state = new RuntimeState();
        state.node = executor.getCurrentNode();
        state.requests = executor.getCompletedRequests();
        state.error = executor.getLastError();
        try {
            Map<String, String> saved = mapper.readValue(executor.getCooldownsJson(), new TypeReference<Map<String, String>>() {});
            saved.forEach((node, until) -> state.cooldowns.put(node, Instant.parse(until)));
        } catch (Exception exception) { throw new IllegalStateException("执行器冷却状态损坏", exception); }
        return state;
    }
    private void persist(CrawlerExecutor executor, RuntimeState state) {
        if (executor == null) return;
        executor.setCurrentNode(state.node);
        executor.setCompletedRequests(state.requests);
        executor.setLastError(state.error);
        state.cooldowns.entrySet().removeIf(entry -> !entry.getValue().isAfter(Instant.now()));
        Map<String, String> cooldowns = new HashMap<>();
        state.cooldowns.forEach((node, until) -> cooldowns.put(node, until.toString()));
        try { executor.setCooldownsJson(mapper.writeValueAsString(cooldowns)); }
        catch (Exception exception) { throw new IllegalStateException("无法保存冷却状态", exception); }
        repository.save(executor);
    }

    private ExecutorView view(CrawlerExecutor executor) {
        RuntimeState state = states.computeIfAbsent(executor.getId(), ignored -> restore(executor));
        return new ExecutorView(executor.getId(), executor.getName(), executor.getEnabled(),
                executor.getProxyMode(), executor.getProxyId(), executor.getProxyGroup(), nodes(executor),
                executor.getSwitchMode(), executor.getRotateAfterRequests(), executor.getCooldownSeconds(),
                state.jobId, state.node, state.requests, state.error, Map.copyOf(state.cooldowns));
    }

    public class Session implements AutoCloseable {
        private final CrawlerExecutor executor;
        private final RuntimeState state;
        private Session(CrawlerExecutor executor, RuntimeState state) { this.executor = executor; this.state = state; }
        public boolean managedMihomo() { return executor != null && "MIHOMO".equals(executor.getProxyMode()); }
        public int nodeCount() { return managedMihomo() ? nodes(executor).size() : 1; }
        public boolean autoFailover() { return managedMihomo() && !"MANUAL".equals(executor.getSwitchMode()); }
        public CrawlerSettingsService.NetworkSnapshot network() throws Exception {
            CrawlerSettingsService.NetworkSnapshot base = settingsService.networkSnapshot();
            if (executor == null || "DEFAULT".equals(executor.getProxyMode())) return base;
            CrawlerExecutor latest = repository.findById(executor.getId()).orElseThrow();
            if (!latest.getEnabled()) throw new IOException("执行器已停用");
            boolean direct = "DIRECT".equals(executor.getProxyMode());
            List<CrawlerSettingsService.ProxyConfig> proxies = List.of();
            if (!direct) {
                CrawlerProxy proxy = requireProxy(executor.getProxyId());
                if (!proxy.getEnabled()) throw new IOException("执行器代理已停用");
                if (managedMihomo()) prepareNode(proxy);
                proxies = List.of(new CrawlerSettingsService.ProxyConfig(proxy.getId(), proxy.getHost(),
                        proxy.getPort(), proxy.getUsername(), secrets.decrypt(proxy.getPasswordCiphertext())));
            }
            return new CrawlerSettingsService.NetworkSnapshot(direct, base.connectTimeoutSeconds(),
                    base.requestTimeoutSeconds(), base.minRequestIntervalMillis(), base.maxRetries(),
                    base.retryBaseDelayMillis(), proxies);
        }
        private void prepareNode(CrawlerProxy proxy) throws Exception {
            synchronized (CrawlerExecutorService.this) {
                List<String> candidates = nodes(executor);
                boolean rotate = "REQUESTS".equals(executor.getSwitchMode())
                        && state.requests >= executor.getRotateAfterRequests();
                if (state.node == null || rotate || state.cooldowns.getOrDefault(state.node, Instant.EPOCH).isAfter(Instant.now())) {
                    String next = null;
                    int start = state.node == null ? 0 : (candidates.indexOf(state.node) + 1) % candidates.size();
                    for (int i = 0; i < candidates.size(); i++) {
                        String candidate = candidates.get((start + i) % candidates.size());
                        if ("MANUAL".equals(executor.getSwitchMode()) && !candidate.equals(candidates.getFirst())) continue;
                        if (!state.cooldowns.getOrDefault(candidate, Instant.EPOCH).isAfter(Instant.now())) { next = candidate; break; }
                    }
                    if (next == null) throw new ControlException("Mihomo 节点仍在冷却，请稍后重试", null);
                    state.node = next;
                    state.requests = 0;
                }
                try {
                    JsonNode all = catalog(proxy).path("proxies");
                    JsonNode group = all.path(executor.getProxyGroup());
                    boolean member = false;
                    for (JsonNode node : group.path("all")) if (state.node.equals(node.asText())) member = true;
                    if (!"Selector".equals(group.path("type").asText()) || !member
                            || all.path(state.node).isMissingNode() || all.path(state.node).has("all")
                            || List.of("DIRECT", "REJECT", "REJECT-DROP", "PASS", "COMPATIBLE").contains(
                                    all.path(state.node).path("type").asText().toUpperCase(Locale.ROOT))) {
                        throw new IOException("Mihomo 受控组或所选节点已变更");
                    }
                    String actual = group.path("now").asText();
                    if (!state.node.equals(actual)) mihomo.select(proxy.getControllerUrl(),
                            secrets.decrypt(proxy.getSecretCiphertext()), executor.getProxyGroup(), state.node);
                    state.error = null;
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw exception;
                } catch (Exception exception) {
                    state.error = "Mihomo 控制 API 不可用或切换确认失败";
                    throw new ControlException(state.error, exception);
                }
            }
        }
        public void success() { synchronized (CrawlerExecutorService.this) { state.requests++; state.error = null; persist(executor, state); } }
        public void failure(Exception exception) {
            synchronized (CrawlerExecutorService.this) {
                state.error = exception instanceof ControlException ? exception.getMessage() : "采集请求失败";
                if (managedMihomo() && state.node != null && !(exception instanceof ControlException)) {
                    state.cooldowns.put(state.node, Instant.now().plusSeconds(executor.getCooldownSeconds()));
                }
                persist(executor, state);
            }
        }
        public void close() { synchronized (CrawlerExecutorService.this) { state.jobId = null; current.remove(); } }
    }
    public static class ControlException extends IOException {
        public ControlException(String message, Throwable cause) { super(message, cause); }
    }
    private static class RuntimeState {
        Long jobId;
        String node;
        int requests;
        String error;
        final Map<String, Instant> cooldowns = new HashMap<>();
    }
    public record ExecutorRequest(String name, boolean enabled, String proxyMode, Long proxyId,
            String proxyGroup, List<String> nodes, String switchMode, int rotateAfterRequests, int cooldownSeconds) {}
    public record ExecutorView(Long id, String name, boolean enabled, String proxyMode, Long proxyId,
            String proxyGroup, List<String> nodes, String switchMode, int rotateAfterRequests, int cooldownSeconds,
            Long activeJobId, String currentNode, int completedRequests, String lastError, Map<String, Instant> cooldowns) {}
}
