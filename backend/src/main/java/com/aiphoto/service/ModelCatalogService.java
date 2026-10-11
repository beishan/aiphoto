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
        // Only list artifacts this service can install and actually run.
        return List.of(
                online("chinese-clip-vit-base", "Chinese-CLIP ViT-B/16", ModelType.IMAGE_VECTOR,
                        "https://huggingface.co/OFA-Sys/chinese-clip-vit-base-patch16/resolve/main/clip_cn_vit-b-16.pt",
                        "clip_cn_vit-b-16.pt", 0, "OFA-Sys/Chinese-CLIP"),
                online("insightface-buffalo-l", "InsightFace buffalo_l", ModelType.FACE_DETECTION,
                        "https://github.com/deepinsight/insightface/releases/download/v0.7/buffalo_l.zip",
                        "buffalo_l.zip", 0, "deepinsight/insightface"),
                online("yolov8n", "YOLOv8n", ModelType.OBJECT_DETECTION,
                        "https://github.com/ultralytics/assets/releases/download/v8.2.0/yolov8n.pt",
                        "yolov8n.pt", 6549796, "ultralytics/assets"),
                online("yolov8s", "YOLOv8s", ModelType.OBJECT_DETECTION,
                        "https://github.com/ultralytics/assets/releases/download/v8.2.0/yolov8s.pt",
                        "yolov8s.pt", 0, "ultralytics/assets")
        );
    }

    private Map<String, Object> online(String id, String name, ModelType type,
            String url, String filename, long size, String source) {
        return Map.ofEntries(
                Map.entry("id", id), Map.entry("name", name), Map.entry("typeKey", type.getKey()),
                Map.entry("typeLabel", type.getLabel()), Map.entry("version", "官方权重"),
                Map.entry("size", size), Map.entry("device", "CPU/GPU"), Map.entry("precision", "FP32"),
                Map.entry("performance", "取决于硬件"), Map.entry("source", source),
                Map.entry("url", url), Map.entry("filename", filename));
    }

    /**
     * Maps a model type key to the AI service model name (if supported).
     */
    public String getAiServiceModelName(String typeKey) {
        return switch (typeKey) {
            case "image_vector", "similar_image" -> "clip";
            case "face_detection", "face_recognition" -> "insightface";
            case "object_detection", "image_classification" -> "yolo";
            case "image_caption" -> "blip";
            default -> null; // Not directly supported by AI service yet
        };
    }
}
