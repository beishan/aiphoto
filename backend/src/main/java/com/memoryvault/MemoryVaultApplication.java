package com.memoryvault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableAsync;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@SpringBootApplication
@EnableAsync
@EnableCaching
@EnableJpaRepositories(basePackages = "com.memoryvault.repository")
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class MemoryVaultApplication {

    public static void main(String[] args) {
        SpringApplication.run(MemoryVaultApplication.class, args);
    }
}
