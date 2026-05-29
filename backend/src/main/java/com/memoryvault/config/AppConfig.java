package com.memoryvault.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Value("${app.minio.endpoint}")
    private String minioEndpoint;

    @Value("${app.minio.public-endpoint}")
    private String minioPublicEndpoint;

    @Value("${app.minio.access-key}")
    private String minioAccessKey;

    @Value("${app.minio.secret-key}")
    private String minioSecretKey;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(minioEndpoint)
                .credentials(minioAccessKey, minioSecretKey)
                .build();
    }

    @Bean
    @Qualifier("publicMinioClient")
    public MinioClient publicMinioClient() {
        return MinioClient.builder()
                .endpoint(minioPublicEndpoint)
                .credentials(minioAccessKey, minioSecretKey)
                .build();
    }
}
