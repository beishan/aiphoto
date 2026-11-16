package com.aiphoto.service;

import com.aiphoto.repository.FaceClusterRepository;
import com.aiphoto.storage.LocalStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FacePreviewService {
  private final FaceClusterRepository faces;
  private final LocalStorageService storage;
  private final ObjectMapper json;

  private final java.util.concurrent.Semaphore permits = new java.util.concurrent.Semaphore(2);

  public byte[] preview(Long id) throws Exception {
    if (!permits.tryAcquire(5, java.util.concurrent.TimeUnit.SECONDS)) {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "人脸预览繁忙，请稍后重试");
    }
    try { return render(id); } finally { permits.release(); }
  }

  private byte[] render(Long id) throws Exception {
    var face = faces.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (face.getPhoto() == null || face.getPhoto().getDeletedAt() != null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    try (var input = storage.downloadPhoto(face.getPhoto().getFilePath());
        var output = new ByteArrayOutputStream()) {
      var image = ImageIO.read(input);
      if (image == null) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
      Rectangle box = region(json.readTree(face.getBboxJson()), image.getWidth(), image.getHeight());
      Thumbnails.of(image.getSubimage(box.x, box.y, box.width, box.height))
          .size(256, 256).outputFormat("jpg").outputQuality(0.85).toOutputStream(output);
      return output.toByteArray();
    }
  }

  static Rectangle region(JsonNode bbox, int width, int height) {
    double x = bbox.path("x").asDouble(Double.NaN);
    double y = bbox.path("y").asDouble(Double.NaN);
    double w = bbox.path("w").asDouble(Double.NaN);
    double h = bbox.path("h").asDouble(Double.NaN);
    if (!Double.isFinite(x + y + w + h) || x < 0 || y < 0 || w <= 0 || h <= 0) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "无效的人脸区域");
    }
    // AI stores pixel coordinates; support legacy normalized coordinates as well.
    if (x + w <= 1 && y + h <= 1) { x *= width; w *= width; y *= height; h *= height; }
    int left = Math.max(0, (int) Math.floor(x));
    int top = Math.max(0, (int) Math.floor(y));
    int right = Math.min(width, (int) Math.ceil(x + w - 1e-9));
    int bottom = Math.min(height, (int) Math.ceil(y + h - 1e-9));
    if (right <= left || bottom <= top) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "人脸区域超出图片");
    }
    return new Rectangle(left, top, right - left, bottom - top);
  }
}
