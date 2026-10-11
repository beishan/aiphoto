package com.aiphoto.service;

import com.aiphoto.repository.CrawlerProxyRepository;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CrawlerProxyTestService {
    private final CrawlerProxyRepository repository;
    private final CrawlerSecretService secrets;
    private final MihomoApiClient mihomo;
    private final CrawlerSettingsService settings;

    public TestResult test(Long id) {
        var proxy = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("代理不存在"));
        return test(proxy);
    }

    public TestResult preview(Long id, CrawlerSettingsService.ProxyRequest request) {
        return test(settings.previewProxy(id, request));
    }

    private TestResult test(com.aiphoto.entity.CrawlerProxy proxy) {
        Boolean controllerOk = null;
        if ("MIHOMO".equals(proxy.getProxyType())) {
            try {
                mihomo.proxies(proxy.getControllerUrl(), secrets.decrypt(proxy.getSecretCiphertext()));
                controllerOk = true;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return new TestResult(false, false, "连接测试被中断");
            } catch (Exception exception) { controllerOk = false; }
        }
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .proxy(ProxySelector.of(new InetSocketAddress(proxy.getHost(), proxy.getPort())));
        if (proxy.getUsername() != null && !proxy.getUsername().isBlank()) {
            if (System.getProperty("jdk.http.auth.tunneling.disabledSchemes") == null) {
                System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
            }
            builder.authenticator(new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    if (getRequestorType() != RequestorType.PROXY) return null;
                    String password = secrets.decrypt(proxy.getPasswordCiphertext());
                    return new PasswordAuthentication(proxy.getUsername(), password == null ? new char[0] : password.toCharArray());
                }
            });
        }
        try (HttpClient client = builder.build()) {
            var request = HttpRequest.newBuilder(URI.create("https://www.gstatic.com/generate_204"))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            boolean ok = status == 204;
            return new TestResult(controllerOk, ok, ok ? "代理出口可用" : "代理出口返回 HTTP " + status);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new TestResult(controllerOk, false, "连接测试被中断");
        } catch (Exception exception) {
            return new TestResult(controllerOk, false, "代理出口连接失败，请检查地址、端口和认证信息");
        }
    }
    public record TestResult(Boolean controllerOk, boolean proxyOk, String message) {}
}
