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
        proxy.setName(requireText(request.name(), "代理名称", 100));
        proxy.setHost(validateHost(request.host()));
        proxy.setPort(clamp(request.port(), 1, 65535));
        proxy.setUsername(clean(request.username(), 255));
        if (request.clearPassword()) proxy.setPasswordCiphertext(null);
        else if (request.password() != null && !request.password().isBlank()) {
            proxy.setPasswordCiphertext(secretService.encrypt(request.password()));
        }
        proxy.setEnabled(request.enabled());
        if (id == null) proxy.setPriority(proxyRepository.findAllByOrderByPriorityAscIdAsc().size());
        proxy = proxyRepository.save(proxy);
        invalidate();
        return toView(proxy);
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
                setting.getMinRequestIntervalMillis(),
                setting.getMaxRetries(),
                setting.getRetryBaseDelayMillis(),
                proxyRepository.findByEnabledTrueOrderByPriorityAscIdAsc().stream()
                        .map(proxy -> new ProxyConfig(
                                proxy.getId(), proxy.getHost(), proxy.getPort(),
                                proxy.getUsername(), secretService.decrypt(proxy.getPasswordCiphertext())))
                        .toList());
        cachedSnapshot = new CachedSnapshot(snapshot, now + CACHE_MILLIS);
        return snapshot;
    }

    private CrawlerSetting currentSetting() {
        return settingRepository.findById(1L).orElseGet(CrawlerSetting::new);
    }

    private ProxyView toView(CrawlerProxy proxy) {
        return new ProxyView(
                proxy.getId(), proxy.getName(), proxy.getHost(), proxy.getPort(),
                proxy.getUsername(), proxy.getPasswordCiphertext() != null
                        && !proxy.getPasswordCiphertext().isBlank(),
                proxy.getEnabled(), proxy.getPriority());
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
            Integer requestTimeoutSeconds, Long minRequestIntervalMillis,
            Integer maxRetries, Long retryBaseDelayMillis) {}
    public record ProxyRequest(
            String name, String host, Integer port, String username, String password,
            boolean clearPassword, boolean enabled) {}
    public record ProxyView(
            Long id, String name, String host, Integer port, String username,
            boolean hasPassword, boolean enabled, Integer priority) {}
    public record SettingsView(
            boolean directFallback, Integer connectTimeoutSeconds,
            Integer requestTimeoutSeconds, Long minRequestIntervalMillis,
            Integer maxRetries, Long retryBaseDelayMillis,
            List<ProxyView> proxies) {}
    public record ProxyConfig(
            Long id, String host, Integer port, String username, String password) {}
    public record NetworkSnapshot(
            boolean directFallback, int connectTimeoutSeconds,
            int requestTimeoutSeconds, long minRequestIntervalMillis,
            int maxRetries, long retryBaseDelayMillis,
            List<ProxyConfig> proxies) {}
    private record CachedSnapshot(NetworkSnapshot snapshot, long expiresAt) {}
}
