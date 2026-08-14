package com.aiphoto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableAsync;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@SpringBootApplication
@EnableAsync
@EnableJpaRepositories(basePackages = "com.aiphoto.repository")
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class AiphotoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiphotoApplication.class, args);
    }
}
