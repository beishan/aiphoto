package com.aiphoto.service;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

class ModelArtifactServiceTest {
    @TempDir Path directory;

    @Test
    void installsCompleteFacePackageIntoAnIsolatedModelDirectory() throws Exception {
        var management = management();
        var installer = new ModelArtifactService(management);
        Path archive = directory.resolve("face.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (String filename : new String[]{"det_10g.onnx", "w600k_r50.onnx"}) {
                zip.putNextEntry(new ZipEntry(filename));
                zip.write(new byte[]{1, 2});
                zip.closeEntry();
            }
        }
        String path = installer.install(archive, "dl-test", "insightface", "buffalo_l.zip");
        assertThat(path).isEqualTo("installed/dl-test/insightface/models/buffalo_l");
        assertThat(Files.exists(directory.resolve(path).resolve("det_10g.onnx"))).isTrue();
    }

    @Test
    void rejectsArchiveTraversal() throws Exception {
        Path archive = directory.resolve("face.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("../det_10g.onnx"));
            zip.write(new byte[]{1});
            zip.closeEntry();
        }
        var installer = new ModelArtifactService(management());
        assertThatThrownBy(() -> installer.install(archive, "dl-test", "insightface", "buffalo_l.zip"))
                .isInstanceOf(java.io.IOException.class).hasMessageContaining("非法路径");
        assertThat(Files.exists(directory.resolve("det_10g.onnx"))).isFalse();
    }

    private ModelManagementService management() {
        var management = new ModelManagementService(new RestTemplate());
        ReflectionTestUtils.setField(management, "configuredRoot", directory.toString());
        return management;
    }
}
