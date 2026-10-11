package com.aiphoto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(ints = {0, 12, -1})
    void rejectsTamperedCiphertext(int offset) {
        String encrypted = service.encrypt("proxy-password");
        byte[] packed = Base64.getDecoder().decode(encrypted.substring("v1:".length()));
        // Flip a bit in the IV, ciphertext or authentication tag; always change actual bytes.
        int index = offset < 0 ? packed.length - 1 : offset;
        packed[index] ^= 1;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(packed);
        assertThat(tampered).isNotEqualTo(encrypted);

        assertThatThrownBy(() -> service.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("无法解密");
    }
}
