package com.memoryvault.ai;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceClient {

    @Value("${app.ai-service-url}")
    private String aiServiceUrl;

    private final RestTemplate restTemplate;

    /**
     * Generate CLIP embedding for an image.
     */
    public EmbeddingResponse embed(byte[] imageData, String filename) {
        return postImage("/ai/embed", imageData, filename, EmbeddingResponse.class);
    }

    /**
     * Generate CLIP embedding for text.
     */
    public EmbeddingResponse embedText(String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of("text", text);
        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<EmbeddingResponse> response = restTemplate.exchange(
                aiServiceUrl + "/ai/embed-text",
                HttpMethod.POST,
                requestEntity,
                EmbeddingResponse.class
        );
        return response.getBody();
    }

    /**
     * Detect faces in an image.
     */
    public FaceDetectionResponse detectFaces(byte[] imageData, String filename) {
        return postImage("/ai/detect-faces", imageData, filename, FaceDetectionResponse.class);
    }

    /**
     * Classify image using YOLOv8.
     */
    public ClassifyResponse classify(byte[] imageData, String filename) {
        return postImage("/ai/classify", imageData, filename, ClassifyResponse.class);
    }

    /**
     * Generate caption using BLIP-2.
     */
    public CaptionResponse caption(byte[] imageData, String filename) {
        return postImage("/ai/caption", imageData, filename, CaptionResponse.class);
    }

    /**
     * Batch embed multiple images.
     */
    public BatchEmbedResponse batchEmbed(List<byte[]> images, List<String> filenames) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (int i = 0; i < images.size(); i++) {
            final String filename = filenames.get(i);
            final byte[] imageData = images.get(i);
            body.add("files", new ByteArrayResource(imageData) {
                @Override
                public String getFilename() {
                    return filename;
                }
            });
        }

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<BatchEmbedResponse> response = restTemplate.exchange(
                aiServiceUrl + "/ai/batch-embed",
                HttpMethod.POST,
                requestEntity,
                BatchEmbedResponse.class
        );
        return response.getBody();
    }

    private <T> T postImage(String path, byte[] imageData, String filename, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(imageData) {
            @Override
            public String getFilename() {
                return filename;
            }
        });

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<T> response = restTemplate.exchange(
                aiServiceUrl + path,
                HttpMethod.POST,
                requestEntity,
                responseType
        );
        return response.getBody();
    }

    @Data
    public static class EmbeddingResponse {
        private List<Float> embedding;
    }

    @Data
    public static class FaceDetectionResponse {
        private List<FaceResult> faces;

        @Data
        public static class FaceResult {
            private BBox bbox;
            private List<Float> embedding;
            private Double confidence;
        }

        @Data
        public static class BBox {
            private Double x;
            private Double y;
            private Double w;
            private Double h;
        }
    }

    @Data
    public static class ClassifyResponse {
        private List<TagResult> tags;

        @Data
        public static class TagResult {
            private String name;
            private Double confidence;
            private String category;
        }
    }

    @Data
    public static class CaptionResponse {
        private String caption;
    }

    @Data
    public static class BatchEmbedResponse {
        private List<List<Float>> embeddings;
    }
}
