package com.aiphoto.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Provides a catalog of all supported AI model types and online downloadable models.
 * Each model type can be independently configured with either a local path or an online download.
 */
@Slf4j
@Service
public class ModelCatalogService {

    public enum ModelType {
        FACE_DETECTION("face_detection", "人脸检测模型", "InsightFace Detection", "vision"),
        FACE_RECOGNITION("face_recognition", "人脸识别模型", "InsightFace Recognition", "vision"),
        IMAGE_CLASSIFICATION("image_classification", "图片分类模型", "YOLOv8 Classification", "vision"),
        IMAGE_CAPTION("image_caption", "图片描述模型", "BLIP-2 Caption", "vision"),
        OCR("ocr", "OCR 文字识别模型", "PaddleOCR", "vision"),
        IMAGE_VECTOR("image_vector", "图片向量模型", "Chinese-CLIP", "embedding"),
        SIMILAR_IMAGE("similar_image", "相似图片识别模型", " perceptual Hash + CLIP", "embedding"),
        DUPLICATE_DETECTION("duplicate_detection", "重复照片检测模型", "pHash + MD5", "utility"),
        CONTENT_SAFETY("content_safety", "内容安全识别模型", "Safety Classifier", "safety"),
        OBJECT_DETECTION("object_detection", "目标检测模型", "YOLOv8 Detection", "vision");

        private final String key;
        private final String label;
        private final String defaultModel;
        private final String category;

        ModelType(String key, String label, String defaultModel, String category) {
            this.key = key;
            this.label = label;
            this.defaultModel = defaultModel;
            this.category = category;
        }

        public String getKey() { return key; }
        public String getLabel() { return label; }
        public String getDefaultModel() { return defaultModel; }
        public String getCategory() { return category; }
    }

    /**
     * Returns the full catalog of model types with metadata.
     */
    public List<Map<String, Object>> getCatalog() {
        return List.of(ModelType.values()).stream().map(t -> Map.<String, Object>of(
                "key", t.getKey(),
                "label", t.getLabel(),
                "defaultModel", t.getDefaultModel(),
                "category", t.getCategory()
        )).toList();
    }

    /**
     * Returns the list of online downloadable models.
     * In a production system, this would fetch from a model registry.
     */
    public List<Map<String, Object>> getOnlineModels() {
        return List.of(
                buildOnlineModel("chinese-clip-vit-base", "Chinese-CLIP ViT-B/32", ModelType.IMAGE_VECTOR,
                        "1.2", 335544320L, "CPU/GPU", "FP32", "~50 张/秒", "OCSAI/Chinese-CLIP"),
                buildOnlineModel("chinese-clip-vit-large", "Chinese-CLIP ViT-L/14", ModelType.IMAGE_VECTOR,
                        "1.2", 891289600L, "GPU", "FP32", "~30 张/秒", "OCSAI/Chinese-CLIP"),
                buildOnlineModel("insightface-buffalo-l", "InsightFace buffalo_l", ModelType.FACE_DETECTION,
                        "2.7", 281018368L, "CPU/GPU", "FP32", "~20 张/秒", "deepinsight/insightface"),
                buildOnlineModel("insightface-antelopev2", "InsightFace antelopev2", ModelType.FACE_RECOGNITION,
                        "2.7", 281018368L, "CPU/GPU", "FP32", "~18 张/秒", "deepinsight/insightface"),
                buildOnlineModel("yolov8n", "YOLOv8n", ModelType.OBJECT_DETECTION,
                        "8.0", 62389248L, "CPU/GPU", "FP32", "~60 张/秒", "ultralytics/yolov8"),
                buildOnlineModel("yolov8s", "YOLOv8s", ModelType.OBJECT_DETECTION,
                        "8.0", 224814080L, "GPU", "FP32", "~40 张/秒", "ultralytics/yolov8"),
                buildOnlineModel("yolov8m", "YOLOv8m", ModelType.IMAGE_CLASSIFICATION,
                        "8.0", 522149888L, "GPU", "FP32", "~25 张/秒", "ultralytics/yolov8"),
                buildOnlineModel("blip2-opt-2.7b", "BLIP-2 OPT-2.7B", ModelType.IMAGE_CAPTION,
                        "1.0", 4831838208L, "GPU", "FP16", "~3 张/秒", "salesforce/blip2"),
                buildOnlineModel("blip2-opt-6.7b", "BLIP-2 OPT-6.7B", ModelType.IMAGE_CAPTION,
                        "1.0", 12079595520L, "GPU (8GB+)", "FP16", "~1.5 张/秒", "salesforce/blip2"),
                buildOnlineModel("paddleocr-ch", "PaddleOCR 中文", ModelType.OCR,
                        "2.6", 157286400L, "CPU/GPU", "FP32", "~15 张/秒", "PaddlePaddle/PaddleOCR"),
                buildOnlineModel("safety-classifier", "内容安全分类器", ModelType.CONTENT_SAFETY,
                        "1.0", 104857600L, "CPU/GPU", "FP32", "~80 张/秒", "unitaryai/detoxify"),
                buildOnlineModel("phash-model", "pHash 感知哈希", ModelType.DUPLICATE_DETECTION,
                        "1.0", 1048576L, "CPU", "N/A", "~200 张/秒", "built-in"),
                buildOnlineModel("clip-similar", "CLIP 相似图片", ModelType.SIMILAR_IMAGE,
                        "1.0", 335544320L, "CPU/GPU", "FP32", "~50 张/秒", "OCSAI/Chinese-CLIP")
        );
    }

    private Map<String, Object> buildOnlineModel(String id, String name, ModelType type,
                                                    String version, long size, String device,
                                                    String precision, String performance, String source) {
        return Map.ofEntries(
                Map.entry("id", id),
                Map.entry("name", name),
                Map.entry("typeKey", type.getKey()),
                Map.entry("typeLabel", type.getLabel()),
                Map.entry("version", version),
                Map.entry("size", size),
                Map.entry("device", device),
                Map.entry("precision", precision),
                Map.entry("performance", performance),
                Map.entry("source", source),
                Map.entry("url", "https://huggingface.co/" + source + "/resolve/main/" + id + ".bin")
        );
    }

    /**
     * Maps a model type key to the AI service model name (if supported).
     */
    public String getAiServiceModelName(String typeKey) {
        return switch (typeKey) {
            case "image_vector" -> "clip";
            case "face_detection", "face_recognition" -> "insightface";
            case "object_detection", "image_classification" -> "yolo";
            case "image_caption" -> "blip";
            default -> null; // Not directly supported by AI service yet
        };
    }
}
