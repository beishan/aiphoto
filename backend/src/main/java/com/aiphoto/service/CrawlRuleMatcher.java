package com.aiphoto.service;

import java.net.URI;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jsoup.nodes.Element;

public final class CrawlRuleMatcher {

    private CrawlRuleMatcher() {}

    public static boolean matches(String url, String includes, String excludes) {
        String candidate = url.toLowerCase(Locale.ROOT);
        List<String> includeRules = rules(includes);
        List<String> excludeRules = rules(excludes);
        boolean included = includeRules.isEmpty()
                || includeRules.stream().anyMatch(candidate::contains);
        return included && excludeRules.stream().noneMatch(candidate::contains);
    }

    public static String filterReason(String url, String includes, String excludes) {
        String candidate = url.toLowerCase(Locale.ROOT);
        List<String> includeRules = rules(includes);
        List<String> excludeRules = rules(excludes);
        List<String> reasons = new ArrayList<>();
        if (!includeRules.isEmpty() && includeRules.stream().noneMatch(candidate::contains)) {
            reasons.add("未命中任一必含片段（要求命中一个：" + String.join("、", includeRules) + "）");
        }
        List<String> matchedExcludes = excludeRules.stream().filter(candidate::contains).toList();
        if (!matchedExcludes.isEmpty()) {
            reasons.add("命中排除片段：" + String.join("、", matchedExcludes));
        }
        return reasons.isEmpty() ? null : String.join("；", reasons);
    }

    public static String extractImageUrl(Element element, String attributes, URI baseUri) {
        for (String attribute : rules(attributes)) {
            String value = element.attr(attribute);
            if (value.isBlank()) continue;
            if ("srcset".equals(attribute)) value = largestSrcsetCandidate(value);
            try {
                URI resolved = baseUri.resolve(value);
                if (resolved.getHost() != null
                        && ("http".equalsIgnoreCase(resolved.getScheme())
                        || "https".equalsIgnoreCase(resolved.getScheme()))) {
                    return resolved.toString();
                }
            } catch (Exception ignored) {
                // Try the next configured attribute.
            }
        }
        return null;
    }

    private static String largestSrcsetCandidate(String srcset) {
        String selected = "";
        double selectedScore = -1;
        for (String candidate : srcset.split(",")) {
            String[] parts = candidate.trim().split("\\s+");
            if (parts.length == 0 || parts[0].isBlank()) continue;
            double score = descriptorScore(parts.length > 1 ? parts[parts.length - 1] : "1x");
            if (score >= selectedScore) {
                selected = parts[0];
                selectedScore = score;
            }
        }
        return selected;
    }

    private static double descriptorScore(String descriptor) {
        try {
            if (descriptor.endsWith("w")) {
                return Double.parseDouble(descriptor.substring(0, descriptor.length() - 1));
            }
            if (descriptor.endsWith("x")) {
                return Double.parseDouble(descriptor.substring(0, descriptor.length() - 1)) * 10_000;
            }
        } catch (NumberFormatException ignored) {
            // An invalid descriptor remains a low-priority candidate.
        }
        return 0;
    }

    private static List<String> rules(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("[,\\n]"))
                .map(String::trim)
                .filter(rule -> !rule.isEmpty())
                .map(rule -> rule.toLowerCase(Locale.ROOT))
                .toList();
    }
}
