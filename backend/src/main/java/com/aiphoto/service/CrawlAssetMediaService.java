package com.aiphoto.service;

import com.aiphoto.entity.CrawlAsset;
import com.aiphoto.repository.PhotoRepository;
import com.aiphoto.storage.LocalStorageService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import lombok.RequiredArgsConstructor;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
@RequiredArgsConstructor
public class CrawlAssetMediaService {
    private static final int THUMBNAIL_SIZE = 400;
    private static final long MAX_WEBP_PIXELS = 100_000_000L;
    private final Semaphore webpDecoders = new Semaphore(2, true);
    private final PhotoRepository photos;
    private final LocalStorageService storage;
    private final CrawlStagingStorageService staging;

    public record Content(byte[] bytes, MediaType type) {}

    public Content read(CrawlAsset asset, boolean thumbnail) throws Exception {
        return read(asset, thumbnail, false);
    }

    public Content read(CrawlAsset asset, boolean thumbnail, boolean boundedThumbnail) throws Exception {
        if (asset.getImportedPhotoId() != null
                && (asset.getStatus() == CrawlAsset.Status.IMPORTED || asset.getStatus() == CrawlAsset.Status.DUPLICATE)) {
            var photo = photos.findById(asset.getImportedPhotoId()).orElse(null);
            if (photo != null && photo.getDeletedAt() == null) {
                if (thumbnail) {
                    String name = CrawlImportedCleanupService.thumbnailName(photo);
                    boolean webp = name.endsWith(".webp");
                    // Imported WebP "thumbnails" may actually be the original.
                    if (!boundedThumbnail || !webp) {
                        return new Content(storage.downloadThumbnailBytes(name),
                                webp ? MediaType.parseMediaType("image/webp") : MediaType.IMAGE_JPEG);
                    }
                    if (asset.getThumbnailPath() == null) {
                        return webpThumbnail(() -> storage.downloadThumbnailBytes(name));
                    }
                } else {
                    return new Content(storage.downloadBytes(photo.getFilePath()),
                            MediaTypeFactory.getMediaType(photo.getOriginalFilename()).orElse(MediaType.APPLICATION_OCTET_STREAM));
                }
            }
        }
        String path = thumbnail && (boundedThumbnail || asset.getThumbnailPath() != null)
                ? asset.getThumbnailPath() : asset.getLocalPath();
        if (path == null) {
            if (thumbnail && boundedThumbnail && "image/webp".equals(asset.getContentType())
                    && asset.getLocalPath() != null) {
                return webpThumbnail(() -> staging.read(asset.getLocalPath()));
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return new Content(staging.read(path), thumbnail && asset.getThumbnailPath() != null
                ? MediaType.IMAGE_JPEG : MediaType.parseMediaType(asset.getContentType()));
    }

    private Content webpThumbnail(ImageBytes original) throws Exception {
        webpDecoders.acquire();
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(original.read()))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            var reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if ((long) width * height > MAX_WEBP_PIXELS) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                }
                int sample = Math.max(1, Math.max(width, height) / THUMBNAIL_SIZE);
                var options = reader.getDefaultReadParam();
                options.setSourceSubsampling(sample, sample, 0, 0);
                var image = reader.read(0, options);
                var output = new ByteArrayOutputStream();
                Thumbnails.of(image).size(THUMBNAIL_SIZE, THUMBNAIL_SIZE)
                        .outputFormat("jpg").outputQuality(0.8).toOutputStream(output);
                return new Content(output.toByteArray(), MediaType.IMAGE_JPEG);
            } finally {
                reader.dispose();
            }
        } catch (IOException failure) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "无法生成 WebP 缩略图", failure);
        } finally {
            webpDecoders.release();
        }
    }

    @FunctionalInterface
    private interface ImageBytes {
        byte[] read() throws Exception;
    }
}
