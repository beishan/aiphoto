package com.memoryvault.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.storage.photos-dir:./data/photos}")
    private String photosDir;

    @Value("${app.storage.thumbs-dir:./data/thumbs}")
    private String thumbsDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String photosPath = Paths.get(photosDir).toAbsolutePath().toUri().toString();
        String thumbsPath = Paths.get(thumbsDir).toAbsolutePath().toUri().toString();

        registry.addResourceHandler("/media/photos/**")
                .addResourceLocations(photosPath);

        registry.addResourceHandler("/media/thumbs/**")
                .addResourceLocations(thumbsPath);
    }
}
