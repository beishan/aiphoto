package com.aiphoto.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

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

        registry.addResourceHandler("/media/photos/avatars/**")
                .addResourceLocations(photosPath + "avatars/")
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic().immutable());

        registry.addResourceHandler("/media/photos/dock-icons/**")
                .addResourceLocations(photosPath + "dock-icons/")
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic().immutable());

        registry.addResourceHandler("/media/photos/**")
                .addResourceLocations(photosPath);

        registry.addResourceHandler("/media/thumbs/**")
                .addResourceLocations(thumbsPath);
    }
}
