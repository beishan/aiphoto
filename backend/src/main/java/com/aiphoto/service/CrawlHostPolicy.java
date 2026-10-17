package com.aiphoto.service;

import java.net.IDN;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Site domain denylist. Network-address checks remain the responsibility of SafeHttpFetcher. */
public final class CrawlHostPolicy {
    private CrawlHostPolicy() {}

    public static String normalizeBlacklist(String value) {
        return String.join(",", parse(value));
    }

    static Set<String> parse(String value) {
        Set<String> hosts = new LinkedHashSet<>();
        if (value == null || value.isBlank()) return hosts;
        if (value.length() > 1000) throw new IllegalArgumentException("域名黑名单不能超过 1000 个字符");
        for (String entry : value.split("[,，\\s]+")) {
            if (entry.isBlank()) continue;
            String host;
            try {
                host = IDN.toASCII(withoutTrailingDot(entry.trim()), IDN.USE_STD3_ASCII_RULES)
                        .toLowerCase(Locale.ROOT);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("黑名单只能填写域名，不含协议、端口或路径：" + entry);
            }
            if (host.length() > 253 || !host.matches("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")) {
                throw new IllegalArgumentException("黑名单域名无效：" + entry);
            }
            hosts.add(host);
        }
        return hosts;
    }

    static boolean isBlocked(String host, Set<String> blockedHosts) {
        String normalized = withoutTrailingDot(host).toLowerCase(Locale.ROOT);
        return blockedHosts.stream().anyMatch(blocked ->
                normalized.equals(blocked) || normalized.endsWith("." + blocked));
    }

    public static void requireNotBlocked(URI uri, String blacklist) {
        if (uri.getHost() != null && isBlocked(uri.getHost(), parse(blacklist))) {
            throw new IllegalArgumentException("域名在禁止访问列表中：" + uri.getHost());
        }
    }

    private static String withoutTrailingDot(String value) {
        return value.endsWith(".") ? value.substring(0, value.length() - 1) : value;
    }
}
