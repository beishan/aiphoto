package com.aiphoto.ai;

import java.util.Set;

/** Shared mapping for new detections and legacy object/scene tags. */
public final class YoloCategories {
    private static final Set<String> ANIMALS = Set.of(
            "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe");
    private static final Set<String> FOODS = Set.of(
            "banana", "apple", "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake");

    private YoloCategories() {}

    public static String category(String name, String fallback) {
        if ("person".equals(name)) return "person";
        if (ANIMALS.contains(name)) return "animal";
        if (FOODS.contains(name)) return "food";
        if ("potted plant".equals(name)) return "plant";
        return fallback == null || fallback.isBlank() ? "object" : fallback;
    }

    public static String icon(String name, String category) {
        String mapped = category(name, category);
        return Set.of("person", "animal", "food", "plant").contains(mapped) ? mapped : null;
    }
}
