package com.aiphoto.service;

import com.aiphoto.entity.CrawlerProxy;
import com.aiphoto.entity.CrawlerSetting;
import com.aiphoto.repository.CrawlerProxyRepository;
import com.aiphoto.repository.CrawlerSettingRepository;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CrawlerSettingsService {

    private static final long CACHE_MILLIS = 5000;
    private final CrawlerSettingRepository settingRepository;
    private final CrawlerProxyRepository proxyRepository;
    private final CrawlerSecretService secretService;
    private volatile CachedSnapshot cachedSnapshot;

    public SettingsView getSettings() {
        CrawlerSetting setting = currentSetting();
        return new SettingsView(
                setting.getDirectFallback(),
                setting.getConnectTimeoutSeconds(),
                setting.getRequestTimeoutSeconds(),
                setting.getImageDownloadTimeoutSeconds(),
                setting.getMinRequestIntervalMillis(),
                setting.getMaxRetries(),
                setting.getRetryBaseDelayMillis(),
                proxyRepository.findAllByOrderByPriorityAscIdAsc().stream()
                        .map(this::toView).toList());
    }

    @Transactional
    public SettingsView updateSettings(SettingsRequest request) {
        CrawlerSetting setting = currentSetting();
        setting.setDirectFallback(request.directFallback());
        setting.setConnectTimeoutSeconds(clamp(request.connectTimeoutSeconds(), 1, 60));
        setting.setRequestTimeoutSeconds(clamp(request.requestTimeoutSeconds(), 1, 300));
        setting.setImageDownloadTimeoutSeconds(clamp(request.imageDownloadTimeoutSeconds(), 1, 600));
        if (request.minRequestIntervalMillis() == null) {
            throw new IllegalArgumentException("请求间隔不能为空");
        }
        setting.setMinRequestIntervalMillis(Math.max(
                0, Math.min(request.minRequestIntervalMillis(), 60_000L)));
        setting.setMaxRetries(clamp(request.maxRetries(), 0, 5));
        if (request.retryBaseDelayMillis() == null) {
            throw new IllegalArgumentException("重试基础等待不能为空");
        }
        setting.setRetryBaseDelayMillis(Math.max(
                0, Math.min(request.retryBaseDelayMillis(), 60_000L)));
        settingRepository.save(setting);
        invalidate();
        return getSettings();
    }

    @Transactional
    public ProxyView saveProxy(Long id, ProxyRequest request) {
        CrawlerProxy proxy = id == null ? new CrawlerProxy() : proxyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("代理不存在"));
        configureProxy(proxy, request);
        if (id == null) proxy.setPriority(proxyRepository.findAllByOrderByPriorityAscIdAsc().size());
        proxy = proxyRepository.save(proxy);
        invalidate();
        return toView(proxy);
    }

    /** Build a detached candidate so testing never changes saved routing or credentials. */
    public CrawlerProxy previewProxy(Long id, ProxyRequest request) {
        CrawlerProxy candidate = new CrawlerProxy();
        if (id != null) {
            CrawlerProxy saved = proxyRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("代理不存在"));
            candidate.setProxyType(saved.getProxyType());
            candidate.setControllerUrl(saved.getControllerUrl());
            candidate.setPasswordCiphertext(saved.getPasswordCiphertext());
            candidate.setSecretCiphertext(saved.getSecretCiphertext());
        }
        configureProxy(candidate, request);
        return candidate;
    }

    private void configureProxy(CrawlerProxy proxy, ProxyRequest request) {
        proxy.setName(requireText(request.name(), "代理名称", 100));
        proxy.setHost(validateHost(request.host()));
        if (request.port() == null || request.port() < 1 || request.port() > 65535) {
            throw new IllegalArgumentException("代理端口必须在 1 至 65535 之间");
        }
        proxy.setPort(request.port());
        proxy.setUsername(clean(request.username(), 255));
        if (request.clearPassword()) proxy.setPasswordCiphertext(null);
        else if (request.password() != null && !request.password().isBlank()) {
            proxy.setPasswordCiphertext(secretService.encrypt(request.password()));
        }
        String type = request.proxyType() == null ? proxy.getProxyType() : request.proxyType();
        if (!List.of("HTTP", "MIHOMO").contains(type)) throw new IllegalArgumentException("无效代理类型");
        proxy.setProxyType(type);
        if ("MIHOMO".equals(type)) {
            proxy.setControllerUrl(validateControllerUrl(request.controllerUrl() == null
                    ? proxy.getControllerUrl() : request.controllerUrl()));
            if (request.clearSecret()) proxy.setSecretCiphertext(null);
            else if (request.secret() != null && !request.secret().isBlank()) {
                if (request.secret().length() > 500 || request.secret().contains("\r") || request.secret().contains("\n")) {
                    throw new IllegalArgumentException("控制 API 密钥过长或包含换行");
                }
                proxy.setSecretCiphertext(secretService.encrypt(request.secret()));
            }
        } else {
            proxy.setControllerUrl(null);
            proxy.setSecretCiphertext(null);
        }
        proxy.setEnabled(request.enabled());
    }

    @Transactional
    public List<ProxyView> reorder(List<Long> ids) {
        List<CrawlerProxy> proxies = proxyRepository.findAllByOrderByPriorityAscIdAsc();
        List<Long> safeIds = ids == null ? List.of() : ids.stream()
                .filter(java.util.Objects::nonNull).toList();
        if (safeIds.size() != proxies.size()
                || new HashSet<>(safeIds).size() != proxies.size()
                || !new HashSet<>(safeIds).equals(
                        proxies.stream().map(CrawlerProxy::getId).collect(java.util.stream.Collectors.toSet()))) {
            throw new IllegalArgumentException("排序列表必须包含全部代理且不能重复");
        }
        java.util.Map<Long, CrawlerProxy> byId = proxies.stream().collect(
                java.util.stream.Collectors.toMap(CrawlerProxy::getId, proxy -> proxy));
        for (int index = 0; index < safeIds.size(); index++) {
            byId.get(safeIds.get(index)).setPriority(index);
        }
        proxyRepository.flush();
        invalidate();
        return proxyRepository.findAllByOrderByPriorityAscIdAsc().stream().map(this::toView).toList();
    }

    @Transactional
    public void deleteProxy(Long id) {
        if (!proxyRepository.existsById(id)) throw new IllegalArgumentException("代理不存在");
        proxyRepository.deleteById(id);
        List<CrawlerProxy> remaining = proxyRepository.findAllByOrderByPriorityAscIdAsc();
        for (int index = 0; index < remaining.size(); index++) remaining.get(index).setPriority(index);
        proxyRepository.flush();
        invalidate();
    }

    public NetworkSnapshot networkSnapshot() {
        CachedSnapshot cached = cachedSnapshot;
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAt() > now) return cached.snapshot();
        CrawlerSetting setting = currentSetting();
        NetworkSnapshot snapshot = new NetworkSnapshot(
                setting.getDirectFallback(),
                setting.getConnectTimeoutSeconds(),
                setting.getRequestTimeoutSeconds(),
                setting.getImageDownloadTimeoutSeconds(),
                setting.getMinRequestIntervalMillis(),
                setting.getMaxRetries(),
                setting.getRetryBaseDelayMillis(),
                proxyRepository.findByEnabledTrueOrderByPriorityAscIdAsc().stream()
                        .filter(proxy -> "HTTP".equals(proxy.getProxyType()))
                        .map(proxy -> new ProxyConfig(
                                proxy.getId(), proxy.getHost(), proxy.getPort(),
                                proxy.getUsername(), secretService.decrypt(proxy.getPasswordCiphertext())))
                        .toList());
        cachedSnapshot = new CachedSnapshot(snapshot, now + CACHE_MILLIS);
        return snapshot;
    }

    public List<InspectionProxyView> inspectionProxies() {
        return proxyRepository.findAllByOrderByPriorityAscIdAsc().stream()
                .map(proxy -> new InspectionProxyView(
                        proxy.getId(), proxy.getName(), Boolean.TRUE.equals(proxy.getEnabled()),
                        proxy.getProxyType()))
                .toList();
    }

    public String validateInspectionProxy(String mode, Long proxyId) {
        String normalized = mode == null ? "DEFAULT" : mode;
        if (!List.of("DEFAULT", "DIRECT", "SELECTED").contains(normalized)) {
            throw new IllegalArgumentException("无效的网站测试代理方式");
        }
        if ("SELECTED".equals(normalized)) requireInspectionProxy(proxyId);
        return normalized;
    }

    private CrawlerProxy requireInspectionProxy(Long id) {
        if (id == null) throw new IllegalArgumentException("请选择网站测试使用的代理");
        CrawlerProxy proxy = proxyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("网站测试代理已删除，请重新选择"));
        if (!Boolean.TRUE.equals(proxy.getEnabled())) {
            throw new IllegalArgumentException("网站测试代理已停用，请重新选择");
        }
        return proxy;
    }

    public NetworkSnapshot inspectionNetwork(String mode, Long proxyId) {
        String normalized = validateInspectionProxy(mode, proxyId);
        if ("DEFAULT".equals(normalized)) return networkSnapshot();
        CrawlerSetting setting = currentSetting();
        List<ProxyConfig> proxies = List.of();
        if ("SELECTED".equals(normalized)) {
            CrawlerProxy proxy = requireInspectionProxy(proxyId);
            proxies = List.of(new ProxyConfig(proxy.getId(), proxy.getHost(), proxy.getPort(),
                    proxy.getUsername(), secretService.decrypt(proxy.getPasswordCiphertext())));
        }
        return new NetworkSnapshot("DIRECT".equals(normalized),
                setting.getConnectTimeoutSeconds(), setting.getRequestTimeoutSeconds(),
                setting.getImageDownloadTimeoutSeconds(),
                setting.getMinRequestIntervalMillis(), setting.getMaxRetries(),
                setting.getRetryBaseDelayMillis(), proxies);
    }

    public record InspectionProxyView(Long id, String name, boolean enabled, String proxyType) {}

    private CrawlerSetting currentSetting() {
        return settingRepository.findById(1L).orElseGet(CrawlerSetting::new);
    }

    private ProxyView toView(CrawlerProxy proxy) {
        return new ProxyView(
                proxy.getId(), proxy.getName(), proxy.getHost(), proxy.getPort(),
                proxy.getUsername(), proxy.getPasswordCiphertext() != null
                        && !proxy.getPasswordCiphertext().isBlank(),
                proxy.getEnabled(), proxy.getPriority(), proxy.getProxyType(),
                proxy.getControllerUrl(), proxy.getSecretCiphertext() != null);
    }

    public static String validateControllerUrl(String value) {
        if (value == null || value.length() > 500) throw new IllegalArgumentException("请填写控制 API 地址");
        java.net.URI uri;
        try { uri = java.net.URI.create(value.trim()); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("无效控制 API 地址"); }
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null
                || uri.getPort() == 0 || uri.getPort() > 65535
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("控制 API 地址必须是 HTTP/HTTPS 地址且不能包含路径、凭据或查询参数");
        }
        return uri.getScheme() + "://" + uri.getHost().toLowerCase(java.util.Locale.ROOT)
                + ":" + (uri.getPort() == -1 ? ("https".equals(uri.getScheme()) ? 443 : 80) : uri.getPort());
    }

    private String validateHost(String value) {
        String host = requireText(value, "代理主机", 255);
        if (host.contains("://") || host.contains("/") || host.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("代理主机只能填写域名或 IP，不要包含协议和路径");
        }
        return host;
    }

    private String requireText(String value, String label, int maxLength) {
        String result = clean(value, maxLength);
        if (result == null || result.isBlank()) throw new IllegalArgumentException(label + "不能为空");
        return result;
    }

    private String clean(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > maxLength) throw new IllegalArgumentException("配置内容过长");
        return result;
    }

    private int clamp(Integer value, int min, int max) {
        if (value == null) throw new IllegalArgumentException("配置值不能为空");
        return Math.max(min, Math.min(max, value));
    }

    private void invalidate() {
        cachedSnapshot = null;
    }

    public record SettingsRequest(
            boolean directFallback, Integer connectTimeoutSeconds,
            Integer requestTimeoutSeconds, Integer imageDownloadTimeoutSeconds,
            Long minRequestIntervalMillis,
            Integer maxRetries, Long retryBaseDelayMillis) {}
    public record ProxyRequest(
            String name, String host, Integer port, String username, String password,
            boolean clearPassword, boolean enabled, String proxyType,
            String controllerUrl, String secret, boolean clearSecret) {
        public ProxyRequest(String name, String host, Integer port, String username, String password,
                boolean clearPassword, boolean enabled) {
            this(name, host, port, username, password, clearPassword, enabled, null, null, null, false);
        }
    }
    public record ProxyView(
            Long id, String name, String host, Integer port, String username,
            boolean hasPassword, boolean enabled, Integer priority, String proxyType,
            String controllerUrl, boolean secretConfigured) {}
    public record SettingsView(
            boolean directFallback, Integer connectTimeoutSeconds,
            Integer requestTimeoutSeconds, Integer imageDownloadTimeoutSeconds,
            Long minRequestIntervalMillis,
            Integer maxRetries, Long retryBaseDelayMillis,
            List<ProxyView> proxies) {}
    public record ProxyConfig(
            Long id, String host, Integer port, String username, String password) {}
    public record NetworkSnapshot(
            boolean directFallback, int connectTimeoutSeconds,
            int requestTimeoutSeconds, int imageDownloadTimeoutSeconds,
            long minRequestIntervalMillis,
            int maxRetries, long retryBaseDelayMillis,
            List<ProxyConfig> proxies) {}
    private record CachedSnapshot(NetworkSnapshot snapshot, long expiresAt) {}
}
