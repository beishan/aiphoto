package com.aiphoto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiphoto.entity.CrawlerProxy;
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
}
