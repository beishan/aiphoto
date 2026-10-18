package com.aiphoto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiphoto.entity.CrawlerProxy;
import com.aiphoto.entity.CrawlerSetting;
import com.aiphoto.repository.CrawlerProxyRepository;
import com.aiphoto.repository.CrawlerSettingRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CrawlerSettingsServiceTest {

    @Mock private CrawlerSettingRepository settingRepository;
    @Mock private CrawlerProxyRepository proxyRepository;
    @Mock private CrawlerSecretService secretService;
    private CrawlerSettingsService service;

    @BeforeEach
    void setUp() {
        service = new CrawlerSettingsService(settingRepository, proxyRepository, secretService);
    }

    @Test
    void blankPasswordKeepsTheExistingEncryptedPassword() {
        CrawlerProxy proxy = proxy(1L, 0);
        proxy.setPasswordCiphertext("v1:existing");
        when(proxyRepository.findById(1L)).thenReturn(Optional.of(proxy));
        when(proxyRepository.save(proxy)).thenReturn(proxy);

        CrawlerSettingsService.ProxyView result = service.saveProxy(
                1L, new CrawlerSettingsService.ProxyRequest(
                        "Primary", "proxy.example.com", 8080, "user", "", false, true));

        assertThat(proxy.getPasswordCiphertext()).isEqualTo("v1:existing");
        assertThat(result.hasPassword()).isTrue();
    }

    @Test
    void reorderRequiresEveryProxyExactlyOnceAndUpdatesPriorities() {
        CrawlerProxy first = proxy(1L, 0);
        CrawlerProxy second = proxy(2L, 1);
        when(proxyRepository.findAllByOrderByPriorityAscIdAsc())
                .thenReturn(List.of(first, second));

        List<CrawlerSettingsService.ProxyView> result = service.reorder(List.of(2L, 1L));

        assertThat(first.getPriority()).isEqualTo(1);
        assertThat(second.getPriority()).isZero();
        assertThat(result).extracting(CrawlerSettingsService.ProxyView::id)
                .containsExactly(1L, 2L);
        verify(proxyRepository).flush();
    }

    @Test
    void rejectsIncompleteProxyOrder() {
        when(proxyRepository.findAllByOrderByPriorityAscIdAsc())
                .thenReturn(List.of(proxy(1L, 0), proxy(2L, 1)));

        assertThatThrownBy(() -> service.reorder(List.of(1L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("全部代理");
    }

    @Test
    void mihomoSecretIsEncryptedPreservedAndNeverReturned() {
        CrawlerProxy proxy = proxy(1L, 0);
        proxy.setProxyType("MIHOMO"); proxy.setSecretCiphertext("encrypted");
        when(proxyRepository.findById(1L)).thenReturn(Optional.of(proxy));
        when(proxyRepository.save(proxy)).thenReturn(proxy);
        var view = service.saveProxy(1L, new CrawlerSettingsService.ProxyRequest(
                "Mihomo", "localhost", 7895, null, "", false, true, "MIHOMO",
                "http://localhost:9090/", "", false));
        assertThat(proxy.getSecretCiphertext()).isEqualTo("encrypted");
        assertThat(view.secretConfigured()).isTrue();
        assertThat(view.controllerUrl()).isEqualTo("http://localhost:9090");
    }

    @Test
    void mihomoDoesNotEnterDefaultProxyPool() {
        CrawlerProxy ordinary = proxy(1L, 0);
        CrawlerProxy mihomo = proxy(2L, 1); mihomo.setProxyType("MIHOMO");
        when(settingRepository.findById(1L)).thenReturn(Optional.empty());
        when(proxyRepository.findByEnabledTrueOrderByPriorityAscIdAsc()).thenReturn(List.of(ordinary, mihomo));
        assertThat(service.networkSnapshot().proxies()).extracting(CrawlerSettingsService.ProxyConfig::id)
                .containsExactly(1L);
    }

    @Test
    void imageDownloadTimeoutIsSavedAndClamped() {
        CrawlerSetting setting = new CrawlerSetting();
        when(settingRepository.findById(1L)).thenReturn(Optional.of(setting));
        when(proxyRepository.findAllByOrderByPriorityAscIdAsc()).thenReturn(List.of());

        var result = service.updateSettings(new CrawlerSettingsService.SettingsRequest(
                true, 10, 30, 900, 1000L, 2, 1000L));

        assertThat(setting.getImageDownloadTimeoutSeconds()).isEqualTo(600);
        assertThat(result.imageDownloadTimeoutSeconds()).isEqualTo(600);
    }

    @Test
    void committingSettingsInvalidatesSnapshotsReadBeforeTheCommit() {
        CrawlerSetting saved = new CrawlerSetting();
        CrawlerSetting old = new CrawlerSetting();
        when(settingRepository.findById(1L)).thenReturn(Optional.of(saved));
        when(proxyRepository.findAllByOrderByPriorityAscIdAsc()).thenReturn(List.of());
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.updateSettings(new CrawlerSettingsService.SettingsRequest(
                    true, 10, 30, 1, 1000L, 2, 1000L));
            when(settingRepository.findById(1L)).thenReturn(Optional.of(old));
            assertThat(service.networkSnapshot().imageDownloadTimeoutSeconds()).isEqualTo(60);

            when(settingRepository.findById(1L)).thenReturn(Optional.of(saved));
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);

            assertThat(service.networkSnapshot().imageDownloadTimeoutSeconds()).isEqualTo(1);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void controllerUrlRejectsCredentialsAndPaths() {
        assertThatThrownBy(() -> CrawlerSettingsService.validateControllerUrl("http://user:secret@localhost:9090"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CrawlerSettingsService.validateControllerUrl("http://localhost:9090/proxies"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void previewUsesExistingCredentialsWithoutMutatingSavedProxy() {
        CrawlerProxy saved = proxy(1L, 7);
        saved.setProxyType("MIHOMO");
        saved.setControllerUrl("http://old.example.com:9090");
        saved.setPasswordCiphertext("saved-password");
        saved.setSecretCiphertext("saved-secret");
        when(proxyRepository.findById(1L)).thenReturn(Optional.of(saved));

        CrawlerProxy preview = service.previewProxy(1L, new CrawlerSettingsService.ProxyRequest(
                "Draft", "new.example.com", 7890, "user", "", false, true,
                "MIHOMO", "http://new.example.com:9090", "", false));

        assertThat(preview.getPasswordCiphertext()).isEqualTo("saved-password");
        assertThat(preview.getSecretCiphertext()).isEqualTo("saved-secret");
        assertThat(preview.getHost()).isEqualTo("new.example.com");
        assertThat(saved.getHost()).isEqualTo("proxy1.example.com");
        assertThat(saved.getControllerUrl()).isEqualTo("http://old.example.com:9090");
        assertThat(saved.getPriority()).isEqualTo(7);
        org.mockito.Mockito.verify(proxyRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void previewCanReplaceOrClearCredentialsWithoutPersistingThem() {
        CrawlerProxy saved = proxy(1L, 0);
        saved.setProxyType("MIHOMO");
        saved.setPasswordCiphertext("saved-password");
        saved.setSecretCiphertext("saved-secret");
        when(proxyRepository.findById(1L)).thenReturn(Optional.of(saved));
        when(secretService.encrypt("new-secret")).thenReturn("encrypted-new-secret");
        CrawlerProxy preview = service.previewProxy(1L, new CrawlerSettingsService.ProxyRequest(
                "Draft", "example.com", 7890, "", "", true, true,
                "MIHOMO", "http://example.com:9090", "new-secret", false));
        assertThat(preview.getPasswordCiphertext()).isNull();
        assertThat(preview.getSecretCiphertext()).isEqualTo("encrypted-new-secret");
        assertThat(saved.getSecretCiphertext()).isEqualTo("saved-secret");
        assertThat(saved.getPasswordCiphertext()).isEqualTo("saved-password");
    }

    @Test
    void previewValidatesNewConfigurationAndMissingProxy() {
        assertThatThrownBy(() -> service.previewProxy(null, new CrawlerSettingsService.ProxyRequest(
                "Draft", "example.com", 0, "", "", false, true)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("端口");
        when(proxyRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.previewProxy(99L, new CrawlerSettingsService.ProxyRequest(
                "Draft", "example.com", 7890, "", "", false, true)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("代理不存在");
    }

    private CrawlerProxy proxy(Long id, int priority) {
        CrawlerProxy proxy = new CrawlerProxy();
        proxy.setId(id);
        proxy.setName("Proxy " + id);
        proxy.setHost("proxy" + id + ".example.com");
        proxy.setPort(8080);
        proxy.setEnabled(true);
        proxy.setPriority(priority);
        return proxy;
    }
    @Test
    void inspectionDefaultPreservesSystemPolicy() {
        when(proxyRepository.findByEnabledTrueOrderByPriorityAscIdAsc()).thenReturn(List.of());
        assertThat(service.inspectionNetwork("DEFAULT", null)).isSameAs(service.networkSnapshot());
    }

    @Test
    void inspectionDirectIgnoresSystemProxyPool() {
        var network = service.inspectionNetwork("DIRECT", null);
        assertThat(network.directFallback()).isTrue();
        assertThat(network.proxies()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(proxyRepository, secretService);
    }

    @Test
    void inspectionSelectedUsesOnlyChosenMihomoHttpEntryWithCredentials() {
        CrawlerProxy proxy = new CrawlerProxy();
        proxy.setId(7L); proxy.setHost("localhost"); proxy.setPort(7890);
        proxy.setProxyType("MIHOMO"); proxy.setUsername("user"); proxy.setPasswordCiphertext("cipher");
        when(proxyRepository.findById(7L)).thenReturn(Optional.of(proxy));
        when(secretService.decrypt("cipher")).thenReturn("password");

        var network = service.inspectionNetwork("SELECTED", 7L);

        assertThat(network.directFallback()).isFalse();
        assertThat(network.proxies()).containsExactly(
                new CrawlerSettingsService.ProxyConfig(7L, "localhost", 7890, "user", "password"));
        verify(proxyRepository, org.mockito.Mockito.never()).findByEnabledTrueOrderByPriorityAscIdAsc();
    }

    @Test
    void inspectionRejectsDisabledMissingAndInvalidProxies() {
        CrawlerProxy proxy = new CrawlerProxy(); proxy.setEnabled(false);
        when(proxyRepository.findById(7L)).thenReturn(Optional.of(proxy));
        when(proxyRepository.findById(8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.inspectionNetwork("SELECTED", 7L)).hasMessageContaining("停用");
        assertThatThrownBy(() -> service.inspectionNetwork("SELECTED", 8L)).hasMessageContaining("删除");
        assertThatThrownBy(() -> service.inspectionNetwork("SELECTED", null)).hasMessageContaining("请选择");
        assertThatThrownBy(() -> service.inspectionNetwork("UNKNOWN", null)).hasMessageContaining("无效");
    }
}
