package com.aiphoto.controller;

import com.aiphoto.repository.PhotoRepository;
import com.aiphoto.storage.LocalStorageService;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/mobile")
@RequiredArgsConstructor
public class MobileController {
    private final PhotoRepository photos;
    private final LocalStorageService storage;
    @Value("${app.version}")
    private String version;

    @GetMapping("/server-info")
    public Map<String, Object> info() throws java.io.IOException {
        return Map.of("product", "aiphoto", "version", version, "apiVersion", 1, "instanceId", storage.getInstanceId(),
                "libraryMode", "SHARED", "capabilities", java.util.List.of(
                        "album-pagination", "authenticated-media", "preview", "shared-library-edit"));
    }

    @GetMapping(value = "/photos/{id}/preview", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> preview(@PathVariable Long id) throws Exception {
        var photo = photos.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        // Use the existing thumbnail for video, GIF and RAW. Never serve the original as a preview fallback.
        if (photo.getMediaType() != com.aiphoto.entity.Photo.MediaType.PHOTO) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "请使用缩略图预览");
        }
        try (var input = storage.downloadPhoto(photo.getFilePath()); var output = new ByteArrayOutputStream()) {
            Thumbnails.of(input).size(1600, 1600).outputFormat("jpg").outputQuality(0.85).toOutputStream(output);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(output.toByteArray());
        } catch (java.io.IOException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "此格式暂不支持预览");
        }
    }
}
