package com.memoryvault.service;

import com.memoryvault.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class DockIconService {

    private static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final List<String> ICON_NAMES = List.of(
            "photo", "timeline", "tags", "albums", "baby", "search", "settings",
            "trashEmpty", "trashFull");
    private static final Set<String> ICON_NAME_SET = Set.copyOf(ICON_NAMES);
    private static final Map<String, String> SUPPORTED_TYPES = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp");

    @Value("${app.storage.photos-dir:./data/photos}")
    private String photosDir;

    public Map<String, String> getIcons(User user) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String name : ICON_NAMES) {
            Path icon = findIcon(user.getId(), name);
            if (icon != null) {
                result.put(name, "/media/photos/dock-icons/" + user.getId() + "/" + icon.getFileName()
                        + "?v=" + icon.toFile().lastModified());
            }
        }
        return result;
    }

    public Map<String, String> upload(User user, String name, MultipartFile file) throws Exception {
        requireName(name);
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择图标图片");
        if (file.getSize() > MAX_SIZE) throw new IllegalArgumentException("图标图片不能超过5MB");
        byte[] bytes = file.getBytes();
        String extension = SUPPORTED_TYPES.get(detectContentType(bytes));
        if (extension == null) throw new IllegalArgumentException("仅支持 JPG、PNG 或 WebP 图片");

        Path directory = iconDirectory(user.getId());
        Files.createDirectories(directory);
        Path temporary = directory.resolve("." + UUID.randomUUID() + extension);
        Files.write(temporary, bytes);
        deleteExisting(user.getId(), name);
        Files.move(temporary, directory.resolve(name + extension), StandardCopyOption.REPLACE_EXISTING);
        return getIcons(user);
    }

    public Map<String, String> delete(User user, String name) {
        requireName(name);
        deleteExisting(user.getId(), name);
        return getIcons(user);
    }

    private Path iconDirectory(Long userId) {
        return Paths.get(photosDir, "dock-icons", String.valueOf(userId));
    }

    private Path findIcon(Long userId, String name) {
        Path directory = iconDirectory(userId);
        if (!Files.isDirectory(directory)) return null;
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(name + "."))
                    .filter(path -> isSupported(path.getFileName().toString()))
                    .findFirst().orElse(null);
        } catch (Exception e) {
            throw new RuntimeException("图标读取失败", e);
        }
    }

    private void deleteExisting(Long userId, String name) {
        Path directory = iconDirectory(userId);
        if (!Files.isDirectory(directory)) return;
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(name + "."))
                    .forEach(path -> {
                        try { Files.deleteIfExists(path); } catch (Exception ignored) { }
                    });
        } catch (Exception e) {
            throw new RuntimeException("图标删除失败", e);
        }
    }

    private void requireName(String name) {
        if (!ICON_NAME_SET.contains(name)) throw new IllegalArgumentException("无效的 Dock 图标名称");
    }

    private boolean isSupported(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".webp");
    }

    private String detectContentType(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') return "image/png";
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') return "image/webp";
        return null;
    }
}
