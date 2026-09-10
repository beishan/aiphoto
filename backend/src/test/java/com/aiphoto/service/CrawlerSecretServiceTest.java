package com.aiphoto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CrawlerSecretServiceTest {

    private final CrawlerSecretService service = new CrawlerSecretService(
            "test-proxy-secret-that-is-long-enough", "unused-jwt-secret");

    @Test
    void encryptsWithRandomIvAndDecryptsThePassword() {
        String first = service.encrypt("proxy-password");
        String second = service.encrypt("proxy-password");

        assertThat(first).startsWith("v1:").isNotEqualTo(second);
        assertThat(service.decrypt(first)).isEqualTo("proxy-password");
        assertThat(service.decrypt(second)).isEqualTo("proxy-password");
    }

    @Test
    void rejectsTamperedCiphertext() {
        String encrypted = service.encrypt("proxy-password");
        String tampered = encrypted.substring(0, encrypted.length() - 2) + "AA";

        assertThatThrownBy(() -> service.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("无法解密");
    }
}
