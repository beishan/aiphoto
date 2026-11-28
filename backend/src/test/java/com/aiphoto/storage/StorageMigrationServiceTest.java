package com.aiphoto.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiphoto.storage.StorageMigrationService.Status;
import com.aiphoto.storage.StorageRoots.Kind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class StorageMigrationServiceTest {
    @TempDir Path directory;
    private Path photos;
    private Path thumbs;
    private Path staging;
    private Path models;
    private Path pool;
    private StorageMigrationService service;

    @BeforeEach
    void setup() throws Exception {
        photos = Files.createDirectories(directory.resolve("data/photos"));
        thumbs = Files.createDirectories(directory.resolve("data/thumbs"));
        staging = Files.createDirectories(directory.resolve("data/crawl"));
        models = Files.createDirectory(directory.resolve("models"));
        pool = Files.createDirectory(directory.resolve("pool"));
        service = newService();
    }

    @AfterEach
    void shutdown() {
        service.shutdown();
    }

    @Test
    void verifiedCopySwitchesReadsAndWritesAndSurvivesRestart() throws Exception {
        var storage = new LocalStorageService();
        ReflectionTestUtils.setField(storage, "photosDir", photos.toString());
        ReflectionTestUtils.setField(storage, "thumbsDir", thumbs.toString());
        String identity = storage.getInstanceId();
        storage.uploadPhoto(new byte[]{1, 2}, "previous.jpg", "image/jpeg");
        service.add(Kind.PHOTOS, "gallery");
        try (var writer = StorageRoots.writing(Kind.PHOTOS)) {
            service.start(Kind.PHOTOS, "gallery");
            assertThat(service.overview().job().status()).isEqualTo(Status.WAITING);
            storage.uploadPhoto(new byte[]{3}, "last-write.jpg", "image/jpeg");
        }
        assertThat(awaitCompletion()).isEqualTo(Status.COMPLETED);
        assertThat(storage.getInstanceId()).isEqualTo(identity);
        assertThat(storage.downloadBytes("previous.jpg")).containsExactly(1, 2);
        assertThat(storage.downloadBytes("last-write.jpg")).containsExactly(3);
        storage.uploadPhoto(new byte[]{4}, "new.jpg", "image/jpeg");
        assertThat(pool.resolve("gallery/new.jpg")).exists();
        assertThat(photos.resolve("new.jpg")).doesNotExist();
        assertThat(photos.resolve("previous.jpg")).exists();

        try (var restarted = reusableService()) {
            var category = restarted.service.overview().categories().getFirst();
            assertThat(category.active()).isEqualTo(pool.resolve("gallery").toString());
            assertThat(category.locations()).containsExactly(new StorageMigrationService.Location("gallery", false));
        }
    }

    @Test
    void destinationWithExistingFilesCannotBeUsed() throws Exception {
        service.add(Kind.CRAWL, "collection");
        Files.writeString(pool.resolve("collection/existing"), "keep this");
        assertThatThrownBy(() -> service.start(Kind.CRAWL, "collection"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("必须为空");
        assertThat(Files.readString(pool.resolve("collection/existing"))).isEqualTo("keep this");
        assertThat(StorageRoots.active(staging.toString(), Kind.CRAWL)).isEqualTo(staging);
        assertThatThrownBy(() -> service.add(Kind.MODELS, "../outside"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void modelCopyKeepsWeightsAndConfigurationAndExcludesControlFiles() throws Exception {
        Files.writeString(models.resolve("model-config.json"), "{}");
        Files.writeString(models.resolve("weights.pt"), "model bytes");
        service.add(Kind.MODELS, "weights");
        service.start(Kind.MODELS, "weights");
        assertThat(awaitCompletion()).isEqualTo(Status.COMPLETED);
        assertThat(Files.readString(pool.resolve("weights/weights.pt"))).isEqualTo("model bytes");
        assertThat(pool.resolve("weights/model-config.json")).exists();
        assertThat(pool.resolve("weights/.aiphoto-storage")).doesNotExist();
        assertThat(StorageRoots.active(models.toString(), Kind.MODELS)).isEqualTo(pool.resolve("weights"));
    }

    @Test
    void namedPoolsCanBeBrowsedAndNewDirectoriesCanBecomeMigrationTargets() throws Exception {
        Files.createDirectory(pool.resolve("disk1"));
        Files.createDirectory(pool.resolve("disk2"));
        Files.createDirectory(pool.resolve("unmapped"));
        var named = new StorageMigrationService(photos.toString(), thumbs.toString(), staging.toString(),
                models.toString(), pool.toString(), "disk1,disk2", false);
        try {
            assertThat(named.browse("").children()).extracting(StorageMigrationService.DirectoryEntry::name)
                    .containsExactly("disk1", "disk2");
            assertThat(named.browse("disk2").selectable()).isFalse();
            assertThat(named.browse("disk2").creatable()).isTrue();
            var created = named.createDirectory("disk2", "家庭照片");
            assertThat(created.path()).isEqualTo("disk2/家庭照片");
            assertThat(named.browse(created.path()).selectable()).isTrue();
            named.add(Kind.PHOTOS, created.path());
            assertThat(named.overview().categories().getFirst().locations())
                    .containsExactly(new StorageMigrationService.Location(created.path(), true));
            assertThatThrownBy(() -> named.createDirectory("", "unmapped-child")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> named.browse("disk2/../unmapped")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> named.createDirectory(created.path(), "inside-registered"))
                    .isInstanceOf(IllegalArgumentException.class);
        } finally {
            named.shutdown();
        }
    }

    private StorageMigrationService newService() {
        return new StorageMigrationService(photos.toString(), thumbs.toString(), staging.toString(),
                models.toString(), pool.toString(), "", true);
    }

    private RestartedService reusableService() {
        return new RestartedService(newService());
    }

    private record RestartedService(StorageMigrationService service) implements AutoCloseable {
        @Override public void close() { service.shutdown(); }
    }

    private Status awaitCompletion() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            var job = service.overview().job();
            if (job.status() == Status.COMPLETED || job.status() == Status.FAILED) {
                assertThat(job.error()).isNull();
                return job.status();
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Storage migration did not finish");
    }
}
