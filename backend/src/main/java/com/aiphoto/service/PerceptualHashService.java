package com.aiphoto.service;

import java.awt.image.BufferedImage;
import org.springframework.stereotype.Service;

@Service
public class PerceptualHashService {

    public String differenceHash(BufferedImage source) {
        int[][] luminance = new int[8][9];
        for (int y = 0; y < 8; y++) {
            int sourceY = Math.min(source.getHeight() - 1,
                    (int) (((y + 0.5) * source.getHeight()) / 8));
            for (int x = 0; x < 9; x++) {
                int sourceX = Math.min(source.getWidth() - 1,
                        (int) (((x + 0.5) * source.getWidth()) / 9));
                luminance[y][x] = luminance(source.getRGB(sourceX, sourceY));
            }
        }
        long hash = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                hash <<= 1;
                if (luminance[y][x] > luminance[y][x + 1]) hash |= 1;
            }
        }
        return String.format("%016x", hash);
    }

    private int luminance(int argb) {
        int alpha = argb >>> 24;
        int red = argb >>> 16 & 0xff;
        int green = argb >>> 8 & 0xff;
        int blue = argb & 0xff;
        int gray = (299 * red + 587 * green + 114 * blue) / 1000;
        return (gray * alpha + 255 * (255 - alpha)) / 255;
    }

    public int hammingDistance(String first, String second) {
        if (first == null || second == null || first.length() != 16 || second.length() != 16) {
            return Integer.MAX_VALUE;
        }
        try {
            return Long.bitCount(
                    Long.parseUnsignedLong(first, 16) ^ Long.parseUnsignedLong(second, 16));
        } catch (NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }
}
