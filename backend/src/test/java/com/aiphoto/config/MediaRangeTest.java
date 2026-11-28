package com.aiphoto.config;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aiphoto.security.JwtAuthenticationFilter;
import com.aiphoto.security.JwtService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@ContextConfiguration(classes = {SecurityConfig.class, WebConfig.class, JwtAuthenticationFilter.class})
class MediaRangeTest {
    static final Path directory;
    static {
        try {
            directory = Files.createDirectory(Files.createTempDirectory("aiphoto-media-range").resolve("photos"));
            Files.write(directory.resolve("sample.mp4"), new byte[1024]);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.photos-dir", () -> directory.toString());
        registry.add("app.storage.thumbs-dir", () -> directory.toString());
    }
    @Autowired MockMvc mvc;
    @MockBean JwtService jwt;
    @MockBean UserDetailsService users;

    @Test void authorizedMediaSupportsPartialContentAndNeverPublicCache() throws Exception {
        mvc.perform(get("/media/photos/sample.mp4")).andExpect(status().isUnauthorized());
        var user = User.withUsername("member").password("unused").roles("USER").build();
        when(jwt.extractUsername("valid")).thenReturn("member");
        when(users.loadUserByUsername("member")).thenReturn(user);
        when(jwt.isTokenValid("valid", user)).thenReturn(true);
        mvc.perform(get("/media/photos/sample.mp4").header("Authorization", "Bearer valid")
                        .header("Range", "bytes=10-19"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 10-19/1024"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().bytes(new byte[10]));
        mvc.perform(get("/media/photos/sample.mp4").header("Authorization", "Bearer valid")
                        .header("Range", "bytes=2048-4096"))
                .andExpect(status().isRequestedRangeNotSatisfiable());

        var newRoot = Files.createDirectory(directory.resolveSibling("new-disk"));
        var control = Files.createDirectory(directory.resolveSibling(".aiphoto-storage"));
        Files.write(newRoot.resolve("sample.mp4"), new byte[]{4, 5, 6});
        var marker = control.resolve("photos.active");
        Files.writeString(marker, newRoot.toString());
        try {
            mvc.perform(get("/media/photos/sample.mp4").header("Authorization", "Bearer valid"))
                    .andExpect(status().isOk())
                    .andExpect(content().bytes(new byte[]{4, 5, 6}));
        } finally {
            Files.deleteIfExists(marker);
        }
    }
}
