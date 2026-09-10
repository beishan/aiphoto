package com.aiphoto.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class PerceptualHashServiceTest {

    private final PerceptualHashService service = new PerceptualHashService();

    @Test
    void producesStableSixtyFourBitHexHash() {
        BufferedImage image = new BufferedImage(18, 16, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                int gray = x * 10;
                image.setRGB(x, y, 0xff000000 | gray << 16 | gray << 8 | gray);
            }
        }

        String hash = service.differenceHash(image);

        assertThat(hash).hasSize(16).matches("[0-9a-f]{16}");
        assertThat(service.differenceHash(image)).isEqualTo(hash);
    }

    @Test
    void calculatesDistanceByBitsRatherThanHexCharacters() {
        assertThat(service.hammingDistance("0000000000000000", "000000000000000f"))
                .isEqualTo(4);
        assertThat(service.hammingDistance("invalid", "0000000000000000"))
                .isEqualTo(Integer.MAX_VALUE);
    }
}
