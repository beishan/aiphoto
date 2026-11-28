package com.aiphoto.service;

import com.aiphoto.entity.CrawlerProxy;
import com.aiphoto.entity.CrawlerSetting;
import com.aiphoto.repository.CrawlerProxyRepository;
import com.aiphoto.repository.CrawlerSettingRepository;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ModelDownloadProxyService {
    private final CrawlerSettingRepository settings;
    private final CrawlerProxyRepository proxies;
    private final CrawlerSecretService secrets;

    public Configuration getConfiguration() {
        var options = proxies.findByEnabledTrueOrderByPriorityAscIdAsc().stream()
                .map(proxy -> new Option(proxy.getId(), proxy.getName()))
                .toList();
        return new Configuration(currentSettings().getModelDownloadProxyId(), options);
    }

    @Transactional
    public void select(Long proxyId) {
        if (proxyId != null) requireEnabled(proxyId);
        var setting = currentSettings();
        setting.setModelDownloadProxyId(proxyId);
        settings.save(setting);
    }

    HttpClient openSelectedClient() {
        Long proxyId = currentSettings().getModelDownloadProxyId();
        if (proxyId == null) return null;
        return SystemProxyHttpClient.configureProxy(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(20))
                        .followRedirects(HttpClient.Redirect.ALWAYS),
                requireEnabled(proxyId), secrets).build();
    }

    private CrawlerSetting currentSettings() {
        return settings.findById(1L).orElseGet(CrawlerSetting::new);
    }

    private CrawlerProxy requireEnabled(Long proxyId) {
        return proxies.findById(proxyId).filter(proxy -> Boolean.TRUE.equals(proxy.getEnabled()))
                .orElseThrow(() -> new IllegalArgumentException("模型下载代理不可用，请选择已启用的系统代理"));
    }

    public record Option(Long id, String name) {}
    public record Configuration(Long proxyId, List<Option> options) {}
}
