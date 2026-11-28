package com.aiphoto.service;

import com.aiphoto.entity.CrawlerProxy;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.http.HttpClient;

final class SystemProxyHttpClient {
    private SystemProxyHttpClient() {}

    static HttpClient.Builder configureProxy(HttpClient.Builder builder,
            CrawlerProxy proxy, CrawlerSecretService secrets) {
        builder.proxy(ProxySelector.of(new InetSocketAddress(proxy.getHost(), proxy.getPort())));
        if (proxy.getUsername() != null && !proxy.getUsername().isBlank()) {
            builder.authenticator(new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    if (getRequestorType() != RequestorType.PROXY
                            || !proxy.getHost().equalsIgnoreCase(getRequestingHost())
                            || proxy.getPort() != getRequestingPort()) return null;
                    String password = secrets.decrypt(proxy.getPasswordCiphertext());
                    return new PasswordAuthentication(proxy.getUsername(), password == null ? new char[0] : password.toCharArray());
                }
            });
        }
        return builder;
    }

}
