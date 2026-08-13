package com.memoryvault.service;

import com.memoryvault.dto.SiteFaviconStatusDTO;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SiteFaviconService {

    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024;
    private static final byte[] PNG_SIGNATURE = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final String PUBLIC_URL = "/api/site/favicon";

    private final Path faviconPath;

    public SiteFaviconService(@Value("${app.storage.photos-dir:./data/photos}") String photosDir) {
        this.faviconPath = Paths.get(photosDir, "site", "favicon.png").toAbsolutePath().normalize();
    }

    public SiteFaviconStatusDTO getStatus() {
        if (!Files.isRegularFile(faviconPath)) {
            return new SiteFaviconStatusDTO(false, null, 0);
        }

        try {
            return new SiteFaviconStatusDTO(
                true,
                PUBLIC_URL,
                Files.getLastModifiedTime(faviconPath).toMillis()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("读取网站图标状态失败", exception);
        }
    }

    public SiteFaviconStatusDTO save(MultipartFile file) {
        validate(file);

        Path temporaryFile = null;
        try {
            Files.createDirectories(faviconPath.getParent());
            temporaryFile = faviconPath.getParent().resolve("favicon-" + UUID.randomUUID() + ".tmp");
            file.transferTo(temporaryFile);
            try {
                Files.move(
                    temporaryFile,
                    faviconPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, faviconPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return getStatus();
        } catch (IOException exception) {
            throw new IllegalStateException("保存网站图标失败", exception);
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException ignored) {
                    // Best-effort cleanup after a failed write.
                }
            }
        }
    }

    public SiteFaviconStatusDTO restoreDefault() {
        try {
            Files.deleteIfExists(faviconPath);
            return getStatus();
        } catch (IOException exception) {
            throw new IllegalStateException("恢复默认网站图标失败", exception);
        }
    }

    public Path getFaviconPath() {
        if (!Files.isRegularFile(faviconPath)) {
            throw new IllegalArgumentException("尚未设置自定义网站图标");
        }
        return faviconPath;
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择裁剪后的网站图标");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("网站图标不能超过 2MB");
        }

        try {
            byte[] bytes = file.getBytes();
            if (bytes.length < PNG_SIGNATURE.length) {
                throw new IllegalArgumentException("裁剪后的网站图标必须为 PNG 格式");
            }
            for (int index = 0; index < PNG_SIGNATURE.length; index++) {
                if (bytes[index] != PNG_SIGNATURE[index]) {
                    throw new IllegalArgumentException("裁剪后的网站图标必须为 PNG 格式");
                }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("读取网站图标失败", exception);
        }
    }
}
