package com.aiphoto.ai;

/** Converts user-facing similarity percentages to cosine distance thresholds. */
public final class AiThresholds {
    private AiThresholds() {}

    public static double similarity(String percentage, double fallback) {
        if (percentage == null || percentage.isBlank()) return fallback;
        try {
            double value = Double.parseDouble(percentage) / 100.0;
            return Double.isFinite(value) && value >= 0 && value <= 1 ? value : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public static double distance(double similarity) {
        if (!Double.isFinite(similarity) || similarity < 0 || similarity > 1) {
            throw new IllegalArgumentException("相似度必须在 0 到 1 之间");
        }
        return 1.0 - similarity;
    }
}
