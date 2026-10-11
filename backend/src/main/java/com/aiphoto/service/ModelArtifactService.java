package com.aiphoto.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Installs downloaded artifacts into a new directory without overwriting active models. */
@Service
@RequiredArgsConstructor
public class ModelArtifactService {
    private final ModelManagementService modelManagementService;

    public String install(Path source, String taskId, String modelName, String filename) throws IOException {
        String base = "installed/" + taskId + "/";
        if (!"insightface".equals(modelName)) {
            String target = base + filename;
            modelManagementService.copyDownloadedFile(source, target);
            return target;
        }
        String target = base + "insightface/models/buffalo_l";
        int count = 0;
        long bytes = 0;
        boolean detection = false;
        boolean recognition = false;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(source))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path entryPath = Path.of(entry.getName().replace('\\', '/')).normalize();
                if (entryPath.isAbsolute() || entryPath.startsWith("..")) {
                    throw new IOException("模型压缩包包含非法路径");
                }
                if (entry.isDirectory()) continue;
                String name = entryPath.getFileName().toString();
                if (!name.endsWith(".onnx")) continue;
                if (++count > 16) throw new IOException("模型压缩包文件数量异常");
                Path file = modelManagementService.prepareDownloadedTarget(target + "/" + name);
                try (var output = Files.newOutputStream(file)) {
                    byte[] buffer = new byte[65536];
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        bytes += read;
                        if (bytes > 2L * 1024 * 1024 * 1024) throw new IOException("模型压缩包解压大小异常");
                        output.write(buffer, 0, read);
                    }
                }
                detection |= "det_10g.onnx".equals(name);
                recognition |= "w600k_r50.onnx".equals(name);
            }
        }
        if (!detection || !recognition) throw new IOException("模型包缺少 buffalo_l 检测或识别权重");
        return target;
    }
}
