package com.aiphoto.service;

import com.aiphoto.entity.*;
import com.aiphoto.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Single-instance dispatcher. Configuration snapshots remain stable during each request and retry. */
@Service
@RequiredArgsConstructor
public class CrawlerExecutorService {
    private final CrawlerExecutorRepository repository;
    private final CrawlerProxyRepository proxyRepository;
    private final CrawlerSettingsService settingsService;
    private final CrawlerSecretService secrets;
    private final MihomoApiClient mihomo;
    private final ObjectMapper mapper;
    private final CrawlerNodeGroupRepository groupRepository;
    private final CrawlerExecutionLogRepository logRepository;
    private final CrawlJobRepository jobRepository;
    private final CrawlSiteRepository siteRepository;
    private final CrawlerExecutorReceiptRepository receiptRepository;
    private final Map<Long, RuntimeState> states = new HashMap<>();
    private final ThreadLocal<Session> current = new ThreadLocal<>();
    private static final Set<String> BUILTIN = Set.of("DIRECT", "REJECT", "REJECT-DROP", "PASS", "COMPATIBLE");

    public synchronized List<ExecutorView> list() {
        return repository.findAllByOrderByIdAsc().stream().map(this::view).toList();
    }

    @Transactional
    public synchronized ExecutorView save(Long id, ExecutorRequest request) throws Exception {
        if (id == null && repository.count() >= 16) throw new IllegalArgumentException("最多配置 16 个执行器");
        requireText(request.name(), 100, "执行器名称");
        if (request.proxyMode() == null || !Set.of("DEFAULT", "DIRECT", "SELECTED", "MIHOMO").contains(request.proxyMode())) {
            throw new IllegalArgumentException("无效代理模式");
        }
        if (request.switchMode() == null || !Set.of("MANUAL", "FAILOVER", "REQUESTS", "TIME", "PAGE", "IMAGE", "TASK").contains(request.switchMode())) {
            throw new IllegalArgumentException("无效节点切换方式");
        }
        range(request.rotateAfterRequests(), 1, 1000000, "轮换请求数");
        range(request.cooldownSeconds(), 10, 604800, "节点冻结时间");
        CrawlerExecutorOptions options = request.options() == null ? new CrawlerExecutorOptions() : request.options();
        validateOptions(options);
        List<String> nodes = cleanNodes(request.nodes());
        Long proxyId = request.proxyId();
        String controlGroup = request.proxyGroup();
        if ("MIHOMO".equals(request.proxyMode())) {
            if (options.getNodeGroupId() != null) {
                CrawlerNodeGroup group = groupRepository.findById(options.getNodeGroupId())
                        .orElseThrow(() -> new IllegalArgumentException("备用节点组不存在"));
                proxyId = group.getProxyId(); controlGroup = group.getControlGroup(); nodes = readNodes(group.getNodesJson());
            }
            CrawlerProxy proxy = requireMihomo(proxyId);
            if (!proxy.getEnabled()) throw new IllegalArgumentException("Mihomo 代理已停用");
            validateNodes(proxy, controlGroup, nodes);
            if (!Set.of("MANUAL", "FAILOVER").contains(request.switchMode()) && nodes.size() < 2) {
                throw new IllegalArgumentException("定期轮换至少需要两个节点");
            }
            if (options.getManualNode() == null && !nodes.isEmpty()) options.setManualNode(nodes.getFirst());
            if (!nodes.contains(options.getManualNode())) throw new IllegalArgumentException("手动节点必须属于所选备用节点组");
            if (id != null && "MANUAL".equals(request.switchMode())) {
                RuntimeState state = states.get(id);
                if (state != null && cooling(state, options.getManualNode())) throw new IllegalArgumentException("手动节点仍在冻结");
            }
            for (CrawlerExecutor other : repository.findAllByOrderByIdAsc()) {
                if (Objects.equals(other.getId(), id) || !"MIHOMO".equals(other.getProxyMode())) continue;
                CrawlerProxy otherProxy = requireProxy(other.getProxyId());
                if (Objects.equals(proxy.getControllerUrl(), otherProxy.getControllerUrl())
                        && Objects.equals(controlGroup, other.getProxyGroup())) {
                    throw new IllegalArgumentException("同一 Mihomo 受控组只能绑定一个执行器");
                }
            }
        } else if ("SELECTED".equals(request.proxyMode())) {
            if (options.getOrdinaryProxies().isEmpty() && proxyId != null) {
                options.setOrdinaryProxies(List.of(new CrawlerExecutorOptions.ProxyChoice(proxyId, null)));
            }
            if (options.getOrdinaryProxies().isEmpty()) throw new IllegalArgumentException("请至少选择一个普通代理");
            for (var choice : options.getOrdinaryProxies()) {
                CrawlerProxy proxy = requireProxy(choice.proxyId());
                if (!"HTTP".equals(proxy.getProxyType()) || !proxy.getEnabled()) throw new IllegalArgumentException("请选择已启用的普通代理");
            }
            proxyId = options.getOrdinaryProxies().getFirst().proxyId();
        } else proxyId = null;
        CrawlerExecutor executor = id == null ? new CrawlerExecutor() : repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("执行器不存在"));
        if (id != null && active(id) && (!Objects.equals(executor.getProxyMode(), request.proxyMode())
                || ("MIHOMO".equals(executor.getProxyMode()) && !Objects.equals(executor.getProxyId(), proxyId)) || !Objects.equals(executor.getProxyGroup(), controlGroup)
                || !Objects.equals(options(executor).getNodeGroupId(), options.getNodeGroupId()))) {
            throw new IllegalArgumentException("运行中可更新策略；更换代理方式、入口或受控组前请冻结执行器并等待当前任务暂停");
        }
        boolean changedPolicy = !Objects.equals(policyKey(executor.getSwitchMode(), executor.getRotateAfterRequests(),
                readNodes(executor.getNodesJson()), options(executor)), policyKey(request.switchMode(), request.rotateAfterRequests(), nodes, options));
        executor.setName(request.name().trim()); executor.setEnabled(request.enabled());
        executor.setProxyMode(request.proxyMode()); executor.setProxyId(proxyId);
        executor.setProxyGroup("MIHOMO".equals(request.proxyMode()) ? controlGroup : null);
        executor.setNodesJson(mapper.writeValueAsString(nodes)); executor.setOptionsJson(mapper.writeValueAsString(options));
        executor.setSwitchMode(request.switchMode()); executor.setRotateAfterRequests(request.rotateAfterRequests());
        executor.setCooldownSeconds(request.cooldownSeconds());
        executor = repository.saveAndFlush(executor);
        RuntimeState state = state(executor);
        if (changedPolicy) { state.resetPending = true; persist(executor, state); }
        return view(executor);
    }

    private List<Object> policyKey(String mode, int requests, List<String> nodes, CrawlerExecutorOptions options) {
        return Arrays.asList(mode, requests, nodes, options.getSelectionStrategy(), options.getManualNode(), options.isFailover(),
                options.getRotationSeconds(), options.getRotationPages(), options.getRotationImages(), options.getRotationTasks(), options.getNodeGroupId());
    }
    private void validateOptions(CrawlerExecutorOptions options) {
        if (options.getDescription() != null && options.getDescription().length() > 500) throw new IllegalArgumentException("描述最多 500 字");
        if (!Set.of("ORDERED", "RANDOM").contains(Objects.toString(options.getSelectionStrategy(), ""))) throw new IllegalArgumentException("无效选用顺序");
        range(options.getFailureThreshold(), 1, 10, "连续异常阈值");
        range(options.getDefaultProxyCooldownSeconds(), 10, 604800, "普通代理冷却");
        range(options.getRotationSeconds(), 30, 604800, "轮换时间");
        range(options.getRotationPages(), 1, 1000000, "轮换页面数");
        range(options.getRotationImages(), 1, 1000000, "轮换图片数");
        range(options.getRotationTasks(), 1, 1000000, "轮换任务数");
        if (options.getSiteId() != null && !siteRepository.existsById(options.getSiteId())) throw new IllegalArgumentException("站点不存在");
        if (options.getOrdinaryProxies() == null) options.setOrdinaryProxies(List.of());
        Set<Long> ids = new HashSet<>();
        for (var proxy : options.getOrdinaryProxies()) {
            if (proxy == null || proxy.proxyId() == null || !ids.add(proxy.proxyId())) throw new IllegalArgumentException("代理不能为空或重复");
            if (proxy.cooldownSeconds() != null) range(proxy.cooldownSeconds(), 10, 604800, "代理冷却");
        }
    }

    @Transactional
    public synchronized void delete(Long id) {
        if (active(id)) throw new IllegalArgumentException("执行器正在运行，无法删除");
        if (!repository.existsById(id)) throw new IllegalArgumentException("执行器不存在");
        repository.deleteById(id); states.remove(id);
    }

    @Transactional
    public synchronized ExecutorView freeze(Long id, boolean frozen) throws Exception {
        CrawlerExecutor executor = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("执行器不存在"));
        var options = options(executor); options.setFrozen(frozen);
        executor.setOptionsJson(mapper.writeValueAsString(options)); repository.saveAndFlush(executor);
        return view(executor);
    }

    @Transactional
    public synchronized ExecutorView unbind(Long id) throws Exception {
        if (active(id)) throw new IllegalArgumentException("请先冻结并等待当前任务暂停，再解除引用");
        CrawlerExecutor executor = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("执行器不存在"));
        var options = options(executor); options.setNodeGroupId(null); options.setManualNode(null);
        executor.setOptionsJson(mapper.writeValueAsString(options)); executor.setProxyMode("DEFAULT");
        executor.setProxyId(null); executor.setProxyGroup(null); executor.setNodesJson("[]");
        RuntimeState state = state(executor); state.node = null; resetCounters(state); state.resetPending = false;
        repository.saveAndFlush(executor); persist(executor, state); return view(executor);
    }

    public synchronized void assertProxyEditable(Long id) {
        boolean referenced = states.values().stream().anyMatch(state -> state.requestProxyIds.contains(id))
                || repository.existsByProxyId(id) || groupRepository.existsByProxyId(id)
                || repository.findAllByOrderByIdAsc().stream().filter(e -> "SELECTED".equals(e.getProxyMode())).anyMatch(e -> options(e).getOrdinaryProxies().stream().anyMatch(p -> id.equals(p.proxyId())));
        if (referenced) throw new IllegalArgumentException("代理已被执行器或节点组引用，请先解除引用");
    }

    public synchronized void assertProxyUpdateAllowed(Long id, CrawlerSettingsService.ProxyRequest request) {
        CrawlerProxy existing = requireProxy(id);
        String type = request.proxyType() == null ? existing.getProxyType() : request.proxyType();
        String controller = "MIHOMO".equals(type) ? CrawlerSettingsService.validateControllerUrl(
                request.controllerUrl() == null ? existing.getControllerUrl() : request.controllerUrl()) : null;
        if (!Objects.equals(existing.getHost(), request.host() == null ? null : request.host().trim())
                || !Objects.equals(existing.getPort(), request.port()) || !Objects.equals(existing.getProxyType(), type)
                || !Objects.equals(existing.getControllerUrl(), controller)) assertProxyEditable(id);
    }

    public JsonNode nodes(Long proxyId) throws Exception { return catalog(requireMihomo(proxyId)); }
    public int delay(Long proxyId, String node) throws Exception {
        CrawlerProxy proxy = requireMihomo(proxyId);
        if (!concrete(catalog(proxy).path("proxies").path(node == null ? "" : node))) throw new IllegalArgumentException("请选择具体节点");
        return mihomo.delay(proxy.getControllerUrl(), secrets.decrypt(proxy.getSecretCiphertext()), node);
    }
    public List<GroupView> groups(Long proxyId) { return groupRepository.findByProxyIdOrderByIdAsc(proxyId).stream().map(this::groupView).toList(); }
    @Transactional
    public synchronized GroupView saveGroup(Long proxyId, Long id, GroupRequest request) throws Exception {
        CrawlerProxy proxy = requireMihomo(proxyId);
        requireText(request.name(), 100, "节点组名称"); requireText(request.host(), 255, "HTTP 入口主机");
        if (request.host().contains("://") || request.host().contains("/") || request.host().chars().anyMatch(Character::isWhitespace)) throw new IllegalArgumentException("入口主机只能填写域名或 IP");
        range(request.port(), 1, 65535, "入口端口");
        List<String> nodes = cleanNodes(request.nodes()); validateNodes(proxy, request.controlGroup(), nodes);
        CrawlerNodeGroup group = id == null ? new CrawlerNodeGroup() : groupRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("节点组不存在"));
        if (id != null && !Objects.equals(group.getProxyId(), proxyId)) throw new IllegalArgumentException("节点组不属于该系统代理");
        if (id != null && groupUsed(id)) throw new IllegalArgumentException("节点组已被执行器引用，请先解除引用再编辑");
        group.setProxyId(proxyId); group.setName(request.name().trim()); group.setControlGroup(request.controlGroup());
        group.setHost(request.host().trim()); group.setPort(request.port()); group.setNodesJson(mapper.writeValueAsString(nodes));
        return groupView(groupRepository.saveAndFlush(group));
    }
    @Transactional
    public synchronized void deleteGroup(Long proxyId, Long id) {
        CrawlerNodeGroup group = groupRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("节点组不存在"));
        if (!proxyId.equals(group.getProxyId())) throw new IllegalArgumentException("节点组不属于该代理");
        if (groupUsed(id)) throw new IllegalArgumentException("节点组正在被引用，请先解除引用");
        groupRepository.deleteById(id);
    }
    private boolean groupUsed(Long id) { return repository.findAllByOrderByIdAsc().stream().anyMatch(e -> id.equals(options(e).getNodeGroupId())); }
    private GroupView groupView(CrawlerNodeGroup group) { return new GroupView(group.getId(), group.getProxyId(), group.getName(), group.getControlGroup(), group.getHost(), group.getPort(), readNodes(group.getNodesJson())); }
    public Page<CrawlerExecutionLog> logs(Long id, int page, boolean failedOnly) {
        if (!repository.existsById(id)) throw new IllegalArgumentException("执行器不存在");
        PageRequest pageable = PageRequest.of(Math.max(0, page), 20);
        return failedOnly ? logRepository.findByExecutorIdAndFailedTrueOrderByIdDesc(id, pageable) : logRepository.findByExecutorIdOrderByIdDesc(id, pageable);
    }

    public synchronized Session acquire(Long jobId) {
        List<CrawlerExecutor> executors = repository.findAllByOrderByIdAsc();
        if (executors.isEmpty()) {
            if (active(0L)) return null;
            RuntimeState state = states.computeIfAbsent(0L, ignored -> new RuntimeState()); state.jobId = jobId;
            Session session = new Session(null, state); current.set(session); return session;
        }
        Long siteId = jobRepository.findById(jobId).map(CrawlJob::getSiteId).orElse(null);
        List<CrawlerExecutor> matching = executors.stream().filter(e -> Objects.equals(options(e).getSiteId(), siteId) && options(e).getSiteId() != null).toList();
        if (!matching.isEmpty()) executors = matching; // Dedicated site configuration does not fall back to a global executor.
        for (CrawlerExecutor executor : executors) {
            var options = options(executor);
            if (!executor.getEnabled() || options.isFrozen() || active(executor.getId())
                    || (options.getSiteId() != null && !Objects.equals(options.getSiteId(), siteId))) continue;
            if ("MIHOMO".equals(executor.getProxyMode()) && executor.getProxyId() != null) {
                CrawlerProxy proxy = proxyRepository.findById(executor.getProxyId()).orElse(null);
                if (proxy == null || !proxy.getEnabled()) continue;
            }
            if ("SELECTED".equals(executor.getProxyMode())) {
                var choices = options.getOrdinaryProxies().isEmpty() ? List.of(new CrawlerExecutorOptions.ProxyChoice(executor.getProxyId(), null)) : options.getOrdinaryProxies();
                boolean available = choices.stream().map(choice -> proxyRepository.findById(choice.proxyId()).orElse(null))
                        .anyMatch(proxy -> proxy != null && proxy.getEnabled() && "HTTP".equals(proxy.getProxyType()));
                if (!available) continue;
            }
            RuntimeState state = state(executor); state.jobId = jobId;
            Session session = new Session(executor, state); current.set(session); return session;
        }
        return null;
    }
    public Session current() { return current.get(); }
    private boolean active(Long id) { return states.containsKey(id) && states.get(id).jobId != null; }
    private RuntimeState state(CrawlerExecutor executor) { return states.computeIfAbsent(executor.getId(), ignored -> restore(executor)); }
    private CrawlerProxy requireProxy(Long id) {
        if (id == null) throw new IllegalArgumentException("请选择代理");
        return proxyRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("代理不存在"));
    }
    private CrawlerProxy requireMihomo(Long id) {
        CrawlerProxy proxy = requireProxy(id);
        if (!"MIHOMO".equals(proxy.getProxyType())) throw new IllegalArgumentException("代理不是 Mihomo 类型");
        return proxy;
    }
    private JsonNode catalog(CrawlerProxy proxy) throws Exception { return mihomo.proxies(proxy.getControllerUrl(), secrets.decrypt(proxy.getSecretCiphertext())); }
    private boolean concrete(JsonNode item) { return !item.isMissingNode() && !item.has("all") && !BUILTIN.contains(item.path("type").asText().toUpperCase(Locale.ROOT)); }
    private void validateNodes(CrawlerProxy proxy, String groupName, List<String> nodes) throws Exception {
        requireText(groupName, 255, "Selector 受控组");
        if (nodes.isEmpty()) throw new IllegalArgumentException("请选择备用节点");
        JsonNode all = catalog(proxy).path("proxies"), group = all.path(groupName);
        if (!"Selector".equals(group.path("type").asText())) throw new IllegalArgumentException("受控组必须是 Selector");
        Set<String> members = new HashSet<>(); group.path("all").forEach(n -> members.add(n.asText()));
        for (String node : nodes) if (!members.contains(node) || !concrete(all.path(node))) throw new IllegalArgumentException("备用列表只能包含受控组中的具体代理节点: " + node);
    }
    private List<String> cleanNodes(List<String> nodes) {
        List<String> clean = nodes == null ? List.of() : nodes.stream().filter(Objects::nonNull).map(String::trim).filter(n -> !n.isEmpty()).distinct().toList();
        if (clean.size() > 256 || clean.stream().anyMatch(n -> n.length() > 255)) throw new IllegalArgumentException("节点列表过长");
        return clean;
    }
    private List<String> readNodes(String json) { try { return mapper.readValue(json, new TypeReference<List<String>>() {}); } catch (Exception e) { throw new IllegalStateException("节点配置损坏", e); } }
    private CrawlerExecutorOptions options(CrawlerExecutor executor) { try { return mapper.readValue(executor.getOptionsJson(), CrawlerExecutorOptions.class); } catch (Exception e) { throw new IllegalStateException("执行器配置损坏", e); } }
    private static void requireText(String text, int max, String label) { if (text == null || text.isBlank() || text.length() > max) throw new IllegalArgumentException(label + "不能为空或过长"); }
    private static void range(int value, int min, int max, String label) { if (value < min || value > max) throw new IllegalArgumentException(label + "超出范围"); }
    private boolean cooling(RuntimeState state, String key) { return state.cooldowns.getOrDefault(key, Instant.EPOCH).isAfter(Instant.now()); }
    private RuntimeState restore(CrawlerExecutor executor) {
        try {
            RuntimeState state = mapper.readValue(executor.getRuntimeJson(), RuntimeState.class);
            state.node = executor.getCurrentNode(); state.requests = executor.getCompletedRequests(); state.error = executor.getLastError();
            Map<String, String> saved = mapper.readValue(executor.getCooldownsJson(), new TypeReference<Map<String, String>>() {});
            saved.forEach((node, until) -> state.cooldowns.put(node, Instant.parse(until)));
            state.jobId = null; return state;
        } catch (Exception e) { throw new IllegalStateException("执行器运行状态损坏", e); }
    }
    private void persist(CrawlerExecutor executor, RuntimeState state) {
        if (executor == null) return;
        // Merge runtime into the latest saved configuration; never overwrite a hot configuration with an old request snapshot.
        CrawlerExecutor target = repository.findById(executor.getId()).orElse(executor);
        target.setCurrentNode(state.node); target.setCompletedRequests(state.requests); target.setLastError(state.error);
        state.cooldowns.entrySet().removeIf(e -> !e.getValue().isAfter(Instant.now()));
        Map<String, String> cooldowns = new HashMap<>(); state.cooldowns.forEach((node, until) -> cooldowns.put(node, until.toString()));
        try { target.setCooldownsJson(mapper.writeValueAsString(cooldowns)); target.setRuntimeJson(mapper.writeValueAsString(state)); }
        catch (Exception e) { throw new IllegalStateException("无法保存运行状态", e); }
        repository.save(target);
    }
    private void resetCounters(RuntimeState state) { state.requests = 0; state.activeMillis = 0; state.pages = 0; state.images = 0; state.tasks = 0; }
    private void event(RuntimeState state, String from, String to, boolean success, String reason) {
        state.events.addFirst(new SwitchEvent(Instant.now().toString(), from, to, success, reason));
        while (state.events.size() > 50) state.events.removeLast();
    }
    private ExecutorView view(CrawlerExecutor executor) {
        RuntimeState state = state(executor); var options = options(executor);
        List<ProxyStateView> proxyStates = new ArrayList<>();
        if ("MIHOMO".equals(executor.getProxyMode())) {
            for (String node : readNodes(executor.getNodesJson())) proxyStates.add(proxyState(state, node, node, true));
        } else {
            List<CrawlerProxy> proxies = "SELECTED".equals(executor.getProxyMode()) ? options.getOrdinaryProxies().stream().map(p -> proxyRepository.findById(p.proxyId()).orElse(null)).filter(Objects::nonNull).toList()
                    : "DEFAULT".equals(executor.getProxyMode()) ? proxyRepository.findAllByOrderByPriorityAscIdAsc().stream().filter(p -> "HTTP".equals(p.getProxyType())).toList() : List.of();
            if (proxies.isEmpty() && "SELECTED".equals(executor.getProxyMode()) && executor.getProxyId() != null) proxies = List.of(requireProxy(executor.getProxyId()));
            for (var proxy : proxies) proxyStates.add(proxyState(state, "proxy:" + proxy.getId(), proxy.getName(), proxy.getEnabled()));
            if ("SELECTED".equals(executor.getProxyMode())) for (var choice : options.getOrdinaryProxies()) {
                if (proxies.stream().noneMatch(p -> p.getId().equals(choice.proxyId()))) proxyStates.add(proxyState(state, "proxy:" + choice.proxyId(), "已删除代理 #" + choice.proxyId(), false));
            }
        }
        return new ExecutorView(executor.getId(), executor.getName(), executor.getEnabled(), executor.getProxyMode(), executor.getProxyId(), executor.getProxyGroup(), readNodes(executor.getNodesJson()), executor.getSwitchMode(), executor.getRotateAfterRequests(), executor.getCooldownSeconds(), state.jobId, state.node, state.requests, state.error, Map.copyOf(state.cooldowns), options, state.activeMillis, state.pages, state.images, state.tasks, proxyStates, List.copyOf(state.events), state.waitingJobId);
    }
    private ProxyStateView proxyState(RuntimeState state, String key, String name, boolean available) {
        Instant until = state.cooldowns.get(key);
        return new ProxyStateView(key, name, available, state.failures.getOrDefault(key, 0), until == null ? null : until.toString(), state.reasons.get(key));
    }

    @Scheduled(fixedDelayString = "${app.crawler.executor-recovery-interval-ms:30000}")
    public synchronized void resumeWaiting() {
        for (CrawlerExecutor executor : repository.findAllByOrderByIdAsc()) {
            RuntimeState state = state(executor); var options = options(executor);
            if (state.waitingJobId == null || options.isFrozen() || !executor.getEnabled()
                    || (state.waitUntil != null && Instant.parse(state.waitUntil).isAfter(Instant.now()))) continue;
            CrawlJob job = jobRepository.findById(state.waitingJobId).orElse(null);
            if (job != null && job.getStatus() == CrawlJob.Status.PAUSED && job.getErrorMessage() != null
                    && job.getErrorMessage().startsWith("执行器等待：")) {
                job.setStatus(CrawlJob.Status.QUEUED); job.setErrorMessage(null); job.setLeaseOwner(null); job.setLeaseUntil(null); jobRepository.save(job);
            }
            state.waitingJobId = null; state.waitUntil = null; persist(executor, state);
        }
    }

    public class Session implements AutoCloseable {
        private CrawlerExecutor executor;
        private CrawlerExecutorOptions options;
        private final RuntimeState state;
        private final Set<String> triedNodes = new HashSet<>();
        private final Map<Long, Integer> proxyAttempts = new HashMap<>();
        private final Map<String, Integer> nodeAttempts = new HashMap<>();
        private final Map<String, String> failedProxies = new HashMap<>();
        private String requestUrl;
        private String proxyName;
        private String requestNode;
        private long startedNanos;
        private long activeStartedNanos;
        private int attempts;
        private boolean inRequest;
        private boolean forceSwitch;
        private final CrawlJob jobContext;
        private final String siteName;
        private Session(CrawlerExecutor executor, RuntimeState state) { this.executor = executor; this.state = state; this.options = executor == null ? new CrawlerExecutorOptions() : options(executor);
            this.jobContext = jobRepository.findById(state.jobId).orElse(null);
            this.siteName = jobContext == null || jobContext.getSiteId() == null ? null
                    : siteRepository.findById(jobContext.getSiteId()).map(CrawlSite::getName).orElse(null);
        }
        public void begin(String url) throws Exception {
            synchronized (CrawlerExecutorService.this) {
                if (executor != null) { executor = repository.findById(executor.getId()).orElseThrow(); options = options(executor); }
                if (options.isFrozen()) throw waitFor("执行器已人工冻结", null);
                if (state.resetPending) { resetCounters(state); state.resetPending = false; if (managedMihomo() && !readNodes(executor.getNodesJson()).contains(state.node)) state.node = null; }
                triedNodes.clear(); failedProxies.clear(); proxyAttempts.clear(); nodeAttempts.clear(); state.requestProxyIds.clear(); forceSwitch = false;
                requestUrl = url; requestNode = null; proxyName = null; attempts = 0; startedNanos = System.nanoTime(); activeStartedNanos = startedNanos; inRequest = true;
            }
        }
        public boolean managedMihomo() { return executor != null && "MIHOMO".equals(executor.getProxyMode()); }
        public int nodeCount() { return managedMihomo() ? readNodes(executor.getNodesJson()).size() : 1; }
        public boolean autoFailover() { return managedMihomo() && !"MANUAL".equals(executor.getSwitchMode()) && ("FAILOVER".equals(executor.getSwitchMode()) || options.isFailover()); }
        public boolean ordinaryAvailable(Long id) { synchronized (CrawlerExecutorService.this) { return !cooling(state, "proxy:" + id); } }
        public void proxyAttempt(Long id) { synchronized (CrawlerExecutorService.this) { attempts++; proxyName = id == null ? "直连" : requireProxy(id).getName(); requestNode = managedMihomo() ? state.node : null; if (id != null) proxyAttempts.merge(id, 1, Integer::sum); if (requestNode != null) nodeAttempts.merge(requestNode, 1, Integer::sum); } }
        public void proxyFailed(Long id, String reason) { if (id != null) failedProxies.put("proxy:" + id, reason); }
        public void proxySucceeded(Long id) { synchronized (CrawlerExecutorService.this) { if (id != null) { String key = "proxy:" + id; failedProxies.remove(key); state.failures.put(key, 0); } } }
        public CrawlerSettingsService.NetworkSnapshot network() throws Exception {
            var base = settingsService.networkSnapshot();
            boolean direct = executor == null || "DEFAULT".equals(executor.getProxyMode()) ? base.directFallback() : "DIRECT".equals(executor.getProxyMode());
            List<CrawlerSettingsService.ProxyConfig> proxies = new ArrayList<>();
            if (executor == null || "DEFAULT".equals(executor.getProxyMode())) proxies.addAll(base.proxies());
            else if (!"DIRECT".equals(executor.getProxyMode())) {
                if (managedMihomo()) {
                    CrawlerProxy proxy = requireMihomo(executor.getProxyId());
                    if (!proxy.getEnabled()) throw new ControlException("Mihomo 系统代理已停用", null);
                    prepareNode(proxy);
                    String host = proxy.getHost(); int port = proxy.getPort();
                    if (options.getNodeGroupId() != null) {
                        var group = groupRepository.findById(options.getNodeGroupId()).orElseThrow(); host = group.getHost(); port = group.getPort();
                    }
                    proxies.add(new CrawlerSettingsService.ProxyConfig(proxy.getId(), host, port, proxy.getUsername(), secrets.decrypt(proxy.getPasswordCiphertext())));
                } else {
                    var choices = options.getOrdinaryProxies().isEmpty() ? List.of(new CrawlerExecutorOptions.ProxyChoice(executor.getProxyId(), null)) : options.getOrdinaryProxies();
                    for (var choice : choices) {
                        CrawlerProxy proxy = proxyRepository.findById(choice.proxyId()).orElse(null);
                        if (proxy != null && proxy.getEnabled() && "HTTP".equals(proxy.getProxyType())) proxies.add(new CrawlerSettingsService.ProxyConfig(proxy.getId(), proxy.getHost(), proxy.getPort(), proxy.getUsername(), secrets.decrypt(proxy.getPasswordCiphertext())));
                    }
                }
            }
            if (!managedMihomo()) proxies.removeIf(p -> !ordinaryAvailable(p.id()));
            if ("RANDOM".equals(options.getSelectionStrategy()) && !managedMihomo()) Collections.shuffle(proxies);
            if (proxies.isEmpty() && !direct) {
                Instant until = state.cooldowns.values().stream().filter(i -> i.isAfter(Instant.now())).min(Instant::compareTo).orElse(Instant.now().plusSeconds(30));
                throw waitFor("没有可用普通代理", until);
            }
            synchronized (CrawlerExecutorService.this) { proxies.forEach(proxy -> state.requestProxyIds.add(proxy.id())); }
            return new CrawlerSettingsService.NetworkSnapshot(direct, base.connectTimeoutSeconds(), base.requestTimeoutSeconds(), base.minRequestIntervalMillis(), base.maxRetries(), base.retryBaseDelayMillis(), List.copyOf(proxies));
        }
        private WaitingException waitFor(String reason, Instant until) {
            state.waitingJobId = state.jobId; state.waitUntil = until == null ? null : until.toString(); state.error = "执行器等待：" + reason;
            persist(executor, state); return new WaitingException(state.error);
        }
        private boolean rotationDue() {
            return switch (executor.getSwitchMode()) {
                case "REQUESTS" -> state.requests >= executor.getRotateAfterRequests();
                case "TIME" -> state.activeMillis >= options.getRotationSeconds() * 1000L;
                case "PAGE" -> state.pages >= options.getRotationPages();
                case "IMAGE" -> state.images >= options.getRotationImages();
                case "TASK" -> state.tasks >= options.getRotationTasks();
                default -> false;
            };
        }
        private void prepareNode(CrawlerProxy proxy) throws Exception {
            synchronized (CrawlerExecutorService.this) {
                List<String> candidates = readNodes(executor.getNodesJson());
                String wanted = "MANUAL".equals(executor.getSwitchMode()) ? Objects.requireNonNullElse(options.getManualNode(), candidates.getFirst()) : state.node;
                boolean rotate = rotationDue();
                if (wanted == null || rotate || forceSwitch || cooling(state, wanted)) {
                    List<String> available = new ArrayList<>();
                    int start = wanted == null ? 0 : (candidates.indexOf(wanted) + 1) % candidates.size();
                    for (int i = 0; i < candidates.size(); i++) {
                        String candidate = candidates.get((start + i) % candidates.size());
                        if ("MANUAL".equals(executor.getSwitchMode()) && !candidate.equals(wanted)) continue;
                        if (!cooling(state, candidate) && !triedNodes.contains(candidate)) available.add(candidate);
                    }
                    if ("RANDOM".equals(options.getSelectionStrategy()) && available.size() > 1) {
                        available.remove(state.node); Collections.shuffle(available);
                    }
                    if (available.isEmpty()) {
                        boolean allFrozen = candidates.stream().allMatch(n -> cooling(state, n));
                        if (allFrozen || "MANUAL".equals(executor.getSwitchMode())) {
                            Instant until = candidates.stream().map(state.cooldowns::get).filter(Objects::nonNull).filter(i -> i.isAfter(Instant.now())).min(Instant::compareTo).orElse(Instant.now().plusSeconds(30));
                            throw waitFor("Mihomo 节点仍在冻结", until);
                        }
                        throw new IOException("本次请求所有候选节点已尝试");
                    }
                    wanted = available.getFirst();
                }
                String from = state.node;
                try {
                    JsonNode all = catalog(proxy).path("proxies"), group = all.path(executor.getProxyGroup());
                    boolean member = false; for (JsonNode node : group.path("all")) if (wanted.equals(node.asText())) member = true;
                    if (!"Selector".equals(group.path("type").asText()) || !member || !concrete(all.path(wanted))) throw new IOException("受控组或节点已变更");
                    String actual = group.path("now").asText();
                    if (!wanted.equals(actual)) mihomo.select(proxy.getControllerUrl(), secrets.decrypt(proxy.getSecretCiphertext()), executor.getProxyGroup(), wanted);
                    if (!Objects.equals(wanted, from) || !Objects.equals(actual, from)) {
                        event(state, from, wanted, true, forceSwitch ? "请求异常后切换" : rotate ? "达到轮换阈值" : "应用或核对所选节点"); resetCounters(state); activeStartedNanos = System.nanoTime();
                    }
                    state.node = wanted; state.error = null; forceSwitch = false; requestNode = wanted;
                    persist(executor, state);
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw e; }
                catch (Exception e) { event(state, from, wanted, false, "控制 API 或切换确认失败"); throw new ControlException("Mihomo 控制 API 不可用或切换确认失败", e); }
            }
        }
        public void waitIfUnavailable() throws WaitingException {
            if (!managedMihomo()) return;
            synchronized (CrawlerExecutorService.this) {
                List<String> candidates = "MANUAL".equals(executor.getSwitchMode())
                        ? List.of(Objects.requireNonNullElse(options.getManualNode(), readNodes(executor.getNodesJson()).getFirst())) : readNodes(executor.getNodesJson());
                if (candidates.stream().allMatch(n -> cooling(state, n))) {
                    Instant until = candidates.stream().map(state.cooldowns::get).min(Instant::compareTo).orElse(Instant.now().plusSeconds(30));
                    throw waitFor("Mihomo 节点仍在冻结", until);
                }
            }
        }
        public void success() {
            synchronized (CrawlerExecutorService.this) {
                state.requests++; state.error = null;
                if (managedMihomo() && state.node != null) state.failures.put(state.node, 0);
                persist(executor, state);
            }
        }
        private void countFailure(String key, String reason, int cooldown) {
            int failures = state.failures.merge(key, 1, Integer::sum);
            if (failures >= options.getFailureThreshold()) {
                state.cooldowns.put(key, Instant.now().plusSeconds(cooldown)); state.reasons.put(key, reason);
                if (managedMihomo()) event(state, key, key, false, "连续异常达到阈值，冻结 " + cooldown + " 秒");
            }
        }
        public void failure(Exception exception) {
            synchronized (CrawlerExecutorService.this) {
                state.error = exception instanceof ControlException ? exception.getMessage() : "采集请求失败";
                if (managedMihomo() && state.node != null && !(exception instanceof ControlException)) {
                    countFailure(state.node, "采集请求重试耗尽", executor.getCooldownSeconds());
                    CrawlerExecutionLog log = new CrawlerExecutionLog(); log.setExecutorId(executor.getId()); log.setExecutorName(executor.getName());
                    log.setJobId(state.jobId); setContext(log); log.setProxyName(proxyName); log.setProxyNode(state.node); log.setUrl(requestUrl);
                    log.setFailed(true); log.setAttempts(nodeAttempts.getOrDefault(state.node, attempts)); log.setDurationMillis(inRequest ? Math.max(0, (System.nanoTime() - startedNanos) / 1000000L) : 0);
                    log.setDescription("当前节点请求重试耗尽"); logRepository.save(log);
                    triedNodes.add(state.node); forceSwitch = autoFailover();
                }
                persist(executor, state);
            }
        }
        public void finish(Exception error) throws Exception {
            synchronized (CrawlerExecutorService.this) {
                if (!inRequest) return;
                long duration = Math.max(0, (System.nanoTime() - startedNanos) / 1000000L);
                if (!(error instanceof ControlException)) state.activeMillis += Math.max(0, (System.nanoTime() - activeStartedNanos) / 1000000L);
                if (!managedMihomo()) failedProxies.forEach((key, reason) -> {
                    int cooldown = options.getDefaultProxyCooldownSeconds();
                    for (var choice : options.getOrdinaryProxies()) if (key.equals("proxy:" + choice.proxyId()) && choice.cooldownSeconds() != null) cooldown = choice.cooldownSeconds();
                    countFailure(key, reason, cooldown);
                    if (executor != null) {
                        CrawlerExecutionLog proxyLog = new CrawlerExecutionLog(); proxyLog.setExecutorId(executor.getId()); proxyLog.setExecutorName(executor.getName());
                        proxyLog.setJobId(state.jobId); setContext(proxyLog); proxyLog.setProxyName(requireProxy(Long.parseLong(key.substring(6))).getName());
                        proxyLog.setUrl(requestUrl); proxyLog.setFailed(true); proxyLog.setAttempts(proxyAttempts.getOrDefault(Long.parseLong(key.substring(6)), 0)); proxyLog.setDurationMillis(duration);
                        proxyLog.setDescription(reason); logRepository.save(proxyLog);
                    }
                });
                if (executor != null) {
                    CrawlerExecutionLog log = new CrawlerExecutionLog(); log.setExecutorId(executor.getId()); log.setExecutorName(executor.getName());
                    log.setJobId(state.jobId); setContext(log); log.setProxyName(proxyName); log.setProxyNode(requestNode); log.setUrl(requestUrl);
                    log.setFailed(error != null); log.setAttempts(attempts); log.setDurationMillis(duration);
                    log.setDescription(error == null ? "采集请求成功" : error instanceof ControlException ? error.getMessage() : "采集请求失败：" + error.getClass().getSimpleName());
                    logRepository.save(log);
                }
                inRequest = false; state.requestProxyIds.clear(); persist(executor, state);
                if (error != null && !(error instanceof ControlException) && !managedMihomo()) network();
            }
        }
        private void setContext(CrawlerExecutionLog log) {
            log.setSiteName(siteName);
            if (jobContext != null) { log.setJobName(jobContext.getName()); log.setPhase(jobContext.getPhase().name()); }
        }
        public void pageCompleted(Long pageId) { completed("page:" + state.jobId + ":" + pageId, "PAGE"); }
        public void imageCompleted(Long assetId) { completed("image:" + state.jobId + ":" + assetId, "IMAGE"); }
        public void taskCompleted(String phase) { completed("task:" + state.jobId + ":" + phase, "TASK"); }
        private void completed(String key, String type) {
            synchronized (CrawlerExecutorService.this) {
                if (executor == null) return;
                if (receiptRepository.existsByExecutorIdAndReceiptKey(executor.getId(), key)) return;
                CrawlerExecutorReceipt receipt = new CrawlerExecutorReceipt(); receipt.setExecutorId(executor.getId()); receipt.setReceiptKey(key);
                receiptRepository.saveAndFlush(receipt);
                switch (type) { case "PAGE" -> state.pages++; case "IMAGE" -> state.images++; case "TASK" -> state.tasks++; }
                persist(executor, state);
            }
        }
        public void close() { synchronized (CrawlerExecutorService.this) { state.jobId = null; state.requestProxyIds.clear(); current.remove(); } }
    }
    public static class ControlException extends IOException { public ControlException(String message, Throwable cause) { super(message, cause); } }
    public static class WaitingException extends ControlException { public WaitingException(String message) { super(message, null); } }
    @Data
    public static class RuntimeState {
        private Long jobId;
        private String node;
        private int requests;
        private String error;
        @com.fasterxml.jackson.annotation.JsonIgnore
        private Map<String, Instant> cooldowns = new HashMap<>();
        @com.fasterxml.jackson.annotation.JsonIgnore
        private Set<Long> requestProxyIds = new HashSet<>();
        private Map<String, Integer> failures = new HashMap<>();
        private Map<String, String> reasons = new HashMap<>();
        private long activeMillis;
        private int pages;
        private int images;
        private int tasks;
        private LinkedList<SwitchEvent> events = new LinkedList<>();
        private Long waitingJobId;
        private String waitUntil;
        private boolean resetPending;
    }
    public record SwitchEvent(String time, String from, String to, boolean success, String reason) {}
    public record ProxyStateView(String key, String name, boolean available, int failures, String coolingUntil, String reason) {}
    public record ExecutorRequest(String name, boolean enabled, String proxyMode, Long proxyId, String proxyGroup,
            List<String> nodes, String switchMode, int rotateAfterRequests, int cooldownSeconds, CrawlerExecutorOptions options) {
        public ExecutorRequest(String name, boolean enabled, String proxyMode, Long proxyId, String proxyGroup, List<String> nodes,
                String switchMode, int rotateAfterRequests, int cooldownSeconds) {
            this(name, enabled, proxyMode, proxyId, proxyGroup, nodes, switchMode, rotateAfterRequests, cooldownSeconds, null);
        }
    }
    public record ExecutorView(Long id, String name, boolean enabled, String proxyMode, Long proxyId, String proxyGroup,
            List<String> nodes, String switchMode, int rotateAfterRequests, int cooldownSeconds, Long activeJobId, String currentNode,
            int completedRequests, String lastError, Map<String, Instant> cooldowns, CrawlerExecutorOptions options,
            long activeMillis, int completedPages, int completedImages, int completedTasks, List<ProxyStateView> proxyStates,
            List<SwitchEvent> events, Long waitingJobId) {}
    public record GroupRequest(String name, String controlGroup, String host, int port, List<String> nodes) {}
    public record GroupView(Long id, Long proxyId, String name, String controlGroup, String host, int port, List<String> nodes) {}
}
