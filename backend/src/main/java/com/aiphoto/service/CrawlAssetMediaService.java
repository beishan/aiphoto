package com.aiphoto.service;

import com.aiphoto.entity.CrawlAsset;
import com.aiphoto.repository.PhotoRepository;
import com.aiphoto.storage.LocalStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
@RequiredArgsConstructor
public class CrawlAssetMediaService {
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
                } else {
                    return new Content(storage.downloadBytes(photo.getFilePath()),
                            MediaTypeFactory.getMediaType(photo.getOriginalFilename()).orElse(MediaType.APPLICATION_OCTET_STREAM));
                }
            }
        }
        String path = thumbnail && (boundedThumbnail || asset.getThumbnailPath() != null)
                ? asset.getThumbnailPath() : asset.getLocalPath();
        if (path == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return new Content(staging.read(path), thumbnail && asset.getThumbnailPath() != null
                ? MediaType.IMAGE_JPEG : MediaType.parseMediaType(asset.getContentType()));
    }
}
