package com.aiphoto.service;

import com.aiphoto.storage.StorageRoots;
import com.aiphoto.storage.StorageRoots.Kind;
import com.aiphoto.entity.CrawlerSetting;
import com.aiphoto.repository.CrawlerSettingRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CrawlDiskGuard {
    private static final long GIB = 1L << 30;

    private final CrawlerSettingRepository repository;

    @Value("${app.storage.crawl-dir:./data/crawl}")
    private String crawlDir;

    @Value("${app.storage.photos-dir:./data/photos}")
    private String photosDir;

    @Value("${app.storage.thumbs-dir:./data/thumbs}")
    private String thumbsDir;

    public enum Mode { OFF, PERCENT, GIB }

    public record Limit(Mode mode, Double value) {}
    public record Status(Mode mode, double value, boolean blocked) {}

    public record Volume(long total, long free, long usable) {
        public static Volume read(String directory) throws IOException {
            Path path = Path.of(directory).toAbsolutePath().normalize();
            while (path != null && Files.notExists(path)) path = path.getParent();
            if (path == null) throw new IOException("Storage volume unavailable");
            var store = Files.getFileStore(path.toRealPath());
            return new Volume(store.getTotalSpace(), store.getUnallocatedSpace(), store.getUsableSpace());
        }
    }

    public Status status() {
        var setting = repository.findById(1L).orElseGet(CrawlerSetting::new);
        var mode = setting.getDiskLimitMode();
        double value = setting.getDiskLimitValue();
        if (mode == Mode.OFF) return new Status(mode, value, false);
        try {
            for (String path : new String[]{StorageRoots.active(crawlDir, Kind.CRAWL).toString(),
                    StorageRoots.active(photosDir, Kind.PHOTOS).toString(),
                    StorageRoots.active(thumbsDir, Kind.THUMBNAILS).toString()}) {
                Volume volume = Volume.read(path);
                double threshold = mode == Mode.PERCENT ? volume.total() * (value / 100) : value * GIB;
                if (volume.total() <= 0 || volume.usable() <= threshold) {
                    return new Status(mode, value, true);
                }
            }
            return new Status(mode, value, false);
        } catch (IOException | SecurityException | java.io.UncheckedIOException exception) {
            return new Status(mode, value, true);
        }
    }

    public boolean blocked() {
        return status().blocked();
    }

    @Transactional
    public Status update(Limit limit) {
        Double value = limit.value();
        Mode mode = limit.mode();
        if (mode == null || (mode != Mode.OFF && (value == null || !Double.isFinite(value)
                || value <= 0 || value > (mode == Mode.PERCENT ? 100 : Long.MAX_VALUE / GIB)))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入有效的磁盘剩余空间阈值");
        }
        var setting = repository.findById(1L).orElseGet(CrawlerSetting::new);
        setting.setDiskLimitMode(mode);
        setting.setDiskLimitValue(mode == Mode.OFF ? 0 : value);
        repository.saveAndFlush(setting);
        return status();
    }
}
