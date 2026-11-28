package com.aiphoto.storage;

import com.aiphoto.storage.StorageRoots.Kind;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StorageMigrationService {
    private static final String CONTROL_DIRECTORY = ".aiphoto-storage";
    private final EnumMap<Kind, String> defaults = new EnumMap<>(Kind.class);
    private final String poolDirectory;
    private final java.util.concurrent.ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile Job job;

    public StorageMigrationService(
            @Value("${app.storage.photos-dir:./data/photos}") String photos,
            @Value("${app.storage.thumbs-dir:./data/thumbs}") String thumbs,
            @Value("${app.storage.crawl-dir:./data/crawl}") String crawl,
            @Value("${app.ai-model-root:/models}") String models,
            @Value("${app.storage.pool-directory:}") String poolDirectory) {
        defaults.put(Kind.PHOTOS, photos);
        defaults.put(Kind.THUMBNAILS, thumbs);
        defaults.put(Kind.CRAWL, crawl);
        defaults.put(Kind.MODELS, models);
        this.poolDirectory = poolDirectory;
    }

    public synchronized Overview overview() throws IOException {
        var categories = new ArrayList<Category>();
        for (Kind kind : Kind.values()) {
            Path current = active(kind);
            var available = new ArrayList<Location>();
            for (String directory : locations(kind)) {
                boolean ready = false;
                try {
                    Path path = target(directory, false);
                    if (!path.equals(current)) {
                        requireEmpty(path);
                        ready = true;
                    }
                } catch (IOException | IllegalArgumentException | IllegalStateException ignored) {
                    // Missing, offline, current and nonempty locations cannot be destinations.
                }
                available.add(new Location(directory, ready));
            }
            categories.add(new Category(kind, current.toString(), available));
        }
        return new Overview(!poolDirectory.isBlank(), poolDirectory, categories, job);
    }

    public synchronized void add(Kind kind, String directory) throws IOException {
        Path target = target(directory, true);
        for (Kind existing : Kind.values()) {
            Path current = active(existing);
            requireSeparate(target, Files.exists(current) ? current.toRealPath() : current);
            Path original = Path.of(defaults.get(existing)).toAbsolutePath().normalize();
            requireSeparate(target, Files.exists(original) ? original.toRealPath() : original);
            for (String registered : locations(existing)) {
                requireSeparate(target, target(registered, true));
            }
        }
        Files.createDirectories(target);
        requireEmpty(target);
        var locations = loadLocations();
        locations.setProperty(kind.name(), String.join("\n",
                java.util.stream.Stream.concat(locations(kind).stream(), java.util.stream.Stream.of(directory)).toList()));
        Path registry = registry();
        Files.createDirectories(registry.getParent());
        Path temporary = Files.createTempFile(registry.getParent(), "locations-", ".tmp");
        try {
            try (var output = Files.newOutputStream(temporary)) {
                locations.store(output, null);
            }
            Files.move(temporary, registry, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public synchronized Job start(Kind kind, String directory) throws IOException {
        if (job != null && (job.status() == Status.WAITING || job.status() == Status.COPYING)) {
            throw new IllegalStateException("已有存储迁移正在运行");
        }
        if (!locations(kind).contains(directory)) throw new IllegalArgumentException("请选择已添加的存储位置");
        Path source = active(kind);
        Path target = target(directory, false);
        requireSeparate(target, source);
        requireEmpty(target);
        job = new Job(kind, source.toString(), target.toString(), Status.WAITING, 0, 0, null);
        worker.execute(() -> migrate(kind, source, target));
        return job;
    }

    private void migrate(Kind kind, Path source, Path target) {
        try (var ignored = StorageRoots.migrating(kind)) {
            if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("源存储目录不可用");
            }
            if (kind == Kind.MODELS) {
                Path control = StorageRoots.marker(defaults.get(kind), kind).getParent();
                Files.createDirectories(control);
                // The AI service locks the same file when it saves configuration.
                try (var channel = FileChannel.open(control.resolve("models.lock"),
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE); var lock = channel.lock()) {
                    copyAndSwitch(kind, source, target);
                }
            } else {
                copyAndSwitch(kind, source, target);
            }
        } catch (Exception failure) {
            log.error("Storage migration failed for {}", kind, failure);
            update(Status.FAILED, job.files(), job.total(), failure.getMessage());
        }
    }

    private void copyAndSwitch(Kind kind, Path source, Path target) throws IOException {
        requireEmpty(target);
        Path marker = StorageRoots.marker(defaults.get(kind), kind);
        Path temporary = null;
        Path staging = null;
        try {
            staging = Files.createTempDirectory(target.getParent(), ".aiphoto-copy-");
            try (var paths = Files.walk(source)) {
                long total = 0;
                long bytes = 0;
                for (Path path : (Iterable<Path>) paths::iterator) {
                    if (excluded(source, path)) continue;
                    if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                        total++;
                        bytes = Math.addExact(bytes, Files.size(path));
                    } else if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("源目录包含链接或特殊文件：" + source.relativize(path));
                    } else if (Files.isSameFile(path, staging)) {
                        throw new IOException("目标实际位于源目录内");
                    }
                }
                if (bytes > Files.getFileStore(staging).getUsableSpace()) throw new IOException("目标磁盘空间不足");
                update(Status.COPYING, 0, total, null);
            }
            long files = 0;
            try (var paths = Files.walk(source)) {
                for (Path path : (Iterable<Path>) paths::iterator) {
                    if (excluded(source, path)) continue;
                    Path destination = staging.resolve(source.relativize(path));
                    if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                        Files.createDirectories(destination);
                    } else {
                        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("源文件类型发生变化");
                        Files.copy(path, destination, StandardCopyOption.COPY_ATTRIBUTES);
                        if (Files.mismatch(path, destination) != -1) throw new IOException("文件校验失败：" + source.relativize(path));
                        update(Status.COPYING, ++files, job.total(), null);
                    }
                }
            }
            Files.createDirectories(marker.getParent());
            temporary = Files.createTempFile(marker.getParent(), "active-", ".tmp");
            Files.writeString(temporary, target.toString());
            requireEmpty(target);
            // Switch only after the complete verified copy occupies the target.
            Files.delete(target);
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException failure) {
                Files.createDirectories(target);
                throw failure;
            }
            staging = null;
            Files.move(temporary, marker, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            update(Status.COMPLETED, files, job.total(), null);
        } finally {
            if (temporary != null) Files.deleteIfExists(temporary);
            if (staging != null) {
                try (var paths = Files.walk(staging)) {
                    for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                }
            }
        }
    }

    private static boolean excluded(Path root, Path path) {
        return !root.equals(path) && root.relativize(path).startsWith(CONTROL_DIRECTORY);
    }

    private void update(Status status, long files, long total, String error) {
        var previous = job;
        job = new Job(previous.kind(), previous.source(), previous.target(), status, files, total, error);
    }

    private Path active(Kind kind) {
        return StorageRoots.active(defaults.get(kind), kind);
    }

    private Path target(String directory, boolean allowMissing) throws IOException {
        if (poolDirectory.isBlank()) throw new IllegalStateException("请先挂载存储池");
        if (directory == null || directory.isBlank() || directory.contains("\n") || directory.contains("\r")) {
            throw new IllegalArgumentException("请输入存储池内的目录名");
        }
        Path pool = Path.of(poolDirectory).toRealPath();
        Path relative = Path.of(directory);
        if (relative.isAbsolute() || !relative.equals(relative.normalize()) || relative.startsWith("..")
                || !directory.equals(relative.toString())) {
            throw new IllegalArgumentException("目录必须位于存储池内");
        }
        Path current = pool;
        for (Path segment : relative) {
            if (segment.toString().startsWith(".")) throw new IllegalArgumentException("目录名不能以点开头");
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("目录不能包含符号链接或普通文件");
            }
        }
        if (!allowMissing && !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) throw new IOException("目标目录不可用");
        return current;
    }

    private static void requireSeparate(Path first, Path second) {
        if (first.startsWith(second) || second.startsWith(first)) {
            throw new IllegalArgumentException("存储位置不能与现有目录重叠");
        }
    }

    private static void requireEmpty(Path directory) throws IOException {
        try (var entries = Files.list(directory)) {
            if (entries.findAny().isPresent()) throw new IllegalArgumentException("目标目录必须为空");
        }
    }

    private Path registry() {
        return StorageRoots.marker(defaults.get(Kind.PHOTOS), Kind.PHOTOS).resolveSibling("locations.properties");
    }

    private Properties loadLocations() throws IOException {
        var properties = new Properties();
        if (Files.exists(registry())) {
            try (var input = Files.newInputStream(registry())) {
                properties.load(input);
            }
        }
        return properties;
    }

    private List<String> locations(Kind kind) throws IOException {
        return loadLocations().getProperty(kind.name(), "").lines().toList();
    }

    @PreDestroy
    void shutdown() {
        worker.shutdown();
    }

    public enum Status { WAITING, COPYING, COMPLETED, FAILED }
    public record Job(Kind kind, String source, String target, Status status, long files, long total, String error) {}
    public record Location(String directory, boolean ready) {}
    public record Category(Kind kind, String active, List<Location> locations) {}
    public record Overview(boolean enabled, String pool, List<Category> categories, Job job) {}
}
