package com.aiphoto.config;

import com.aiphoto.storage.StorageRoots;
import com.aiphoto.storage.StorageRoots.Kind;
import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.web.servlet.resource.PathResourceResolver;
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
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePrivate().mustRevalidate())
                .resourceChain(false).addResolver(resolver(photosDir, Kind.PHOTOS, "avatars/"));

        registry.addResourceHandler("/media/photos/dock-icons/**")
                .addResourceLocations(photosPath + "dock-icons/")
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePrivate().mustRevalidate())
                .resourceChain(false).addResolver(resolver(photosDir, Kind.PHOTOS, "dock-icons/"));

        registry.addResourceHandler("/media/photos/**")
                .addResourceLocations(photosPath)
                .setCacheControl(CacheControl.noStore())
                .resourceChain(false).addResolver(resolver(photosDir, Kind.PHOTOS, ""));

        registry.addResourceHandler("/media/thumbs/**")
                .addResourceLocations(thumbsPath)
                .setCacheControl(CacheControl.noStore())
                .resourceChain(false).addResolver(resolver(thumbsDir, Kind.THUMBNAILS, ""));
    }

    private PathResourceResolver resolver(String configured, Kind kind, String subdirectory) {
        return new PathResourceResolver() {
            @Override
            protected Resource getResource(String requestPath, Resource location) throws IOException {
                var root = StorageRoots.active(configured, kind);
                return super.getResource(requestPath, new UrlResource(root.toUri().toString() + subdirectory));
            }
        };
    }
}
