package com.aiphoto.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CrawlerSecretService {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public CrawlerSecretService(
            @Value("${app.crawler.proxy-secret:}") String configuredSecret,
            @Value("${app.jwt.secret}") String jwtSecret) {
        String secret = configuredSecret == null || configuredSecret.isBlank()
                ? jwtSecret : configuredSecret;
        if (secret == null || secret.length() < 16) {
            throw new IllegalStateException("爬虫代理加密密钥长度至少需要 16 个字符");
        }
        try {
            key = new SecretKeySpec(
                    MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)),
                    "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("无法初始化代理密码加密", exception);
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (Exception exception) {
            throw new IllegalStateException("无法加密代理密码", exception);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) return null;
        if (!ciphertext.startsWith(PREFIX)) {
            throw new IllegalStateException("代理密码格式无效，请重新保存该代理");
        }
        try {
            byte[] value = Base64.getDecoder().decode(ciphertext.substring(PREFIX.length()));
            if (value.length <= IV_BYTES) throw new IllegalArgumentException("密文过短");
            byte[] iv = java.util.Arrays.copyOfRange(value, 0, IV_BYTES);
            byte[] encrypted = java.util.Arrays.copyOfRange(value, IV_BYTES, value.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("无法解密代理密码，请检查代理加密密钥", exception);
        }
    }
}
