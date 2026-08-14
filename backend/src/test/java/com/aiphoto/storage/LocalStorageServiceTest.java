package com.aiphoto.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class LocalStorageServiceTest {

    private static final String HASH = "abcdef0123456789abcdef0123456789";

    @TempDir
    Path tempDirectory;

    private LocalStorageService storageService;
    private Path photosDirectory;
    private Path thumbsDirectory;

    @BeforeEach
    void setUp() {
        photosDirectory = tempDirectory.resolve("photos");
        thumbsDirectory = tempDirectory.resolve("thumbs");
        storageService = new LocalStorageService();
        ReflectionTestUtils.setField(storageService, "photosDir", photosDirectory.toString());
        ReflectionTestUtils.setField(storageService, "thumbsDir", thumbsDirectory.toString());
    }

    @Test
    void shardsMd5ObjectNamesButKeepsOtherManagedPathsUnchanged() {
        assertThat(LocalStorageService.shardObjectName(HASH + "/original.jpg"))
            .isEqualTo("ab/cd/" + HASH + "/original.jpg");
        assertThat(LocalStorageService.shardObjectName("avatars/12/avatar.png"))
            .isEqualTo("avatars/12/avatar.png");
        assertThat(LocalStorageService.shardObjectName("site/favicon.png"))
            .isEqualTo("site/favicon.png");
    }

    @Test
    void writesAndServesNewPhotosFromTwoLevelShards() throws Exception {
        storageService.init();

        storageService.uploadPhoto("photo".getBytes(), HASH + "/original.jpg", "image/jpeg");
        storageService.uploadThumbnail("thumb".getBytes(), HASH + "/thumb.jpg");

        assertThat(photosDirectory.resolve("ab/cd").resolve(HASH).resolve("original.jpg"))
            .hasContent("photo");
        assertThat(thumbsDirectory.resolve("ab/cd").resolve(HASH).resolve("thumb.jpg"))
            .hasContent("thumb");
        assertThat(storageService.getPhotoUrl(HASH + "/original.jpg"))
            .isEqualTo("/media/photos/ab/cd/" + HASH + "/original.jpg");
        assertThat(storageService.getThumbnailUrl(HASH + "/thumb.jpg"))
            .isEqualTo("/media/thumbs/ab/cd/" + HASH + "/thumb.jpg");
    }

    @Test
    void migratesLegacyDirectoriesAndKeepsLogicalObjectNamesReadable() throws Exception {
        Path legacyPhoto = photosDirectory.resolve(HASH).resolve("original.jpg");
        Path legacyThumb = thumbsDirectory.resolve(HASH).resolve("thumb.jpg");
        Files.createDirectories(legacyPhoto.getParent());
        Files.createDirectories(legacyThumb.getParent());
        Files.writeString(legacyPhoto, "legacy-photo");
        Files.writeString(legacyThumb, "legacy-thumb");

        storageService.init();

        assertThat(photosDirectory.resolve("ab/cd").resolve(HASH).resolve("original.jpg"))
            .hasContent("legacy-photo");
        assertThat(thumbsDirectory.resolve("ab/cd").resolve(HASH).resolve("thumb.jpg"))
            .hasContent("legacy-thumb");
        assertThat(photosDirectory.resolve(HASH)).doesNotExist();
        assertThat(storageService.downloadBytes(HASH + "/original.jpg"))
            .isEqualTo("legacy-photo".getBytes());
    }

    @Test
    void deletesShardedObjectsAndEmptyShardDirectories() throws Exception {
        storageService.init();
        storageService.uploadPhoto("photo".getBytes(), HASH + "/original.jpg", "image/jpeg");

        storageService.deleteObject(HASH + "/original.jpg");

        assertThat(photosDirectory.resolve("ab")).doesNotExist();
    }
}
