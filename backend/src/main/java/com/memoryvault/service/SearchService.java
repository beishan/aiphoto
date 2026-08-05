package com.memoryvault.service;

import com.memoryvault.ai.AiServiceClient;
import com.memoryvault.dto.PhotoDTO;
import com.memoryvault.dto.SearchRequest;
import com.memoryvault.entity.Photo;
import com.memoryvault.repository.PhotoRepository;
import com.memoryvault.storage.LocalStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final PhotoRepository photoRepository;
    private final LocalStorageService storageService;
    private final AiServiceClient aiServiceClient;
    private final SettingService settingService;

    // Chinese to English mapping for better CLIP search
    private static final Map<String, String> ZH_TO_EN = Map.ofEntries(
            Map.entry("宠物", "pet animal cat dog"),
            Map.entry("猫", "cat kitty"),
            Map.entry("狗", "dog puppy"),
            Map.entry("美食", "food meal restaurant"),
            Map.entry("吃", "eating food dining"),
            Map.entry("风景", "landscape scenery nature"),
            Map.entry("海边", "beach ocean sea"),
            Map.entry("日落", "sunset sunset sky"),
            Map.entry("家庭", "family people together"),
            Map.entry("人物", "person people portrait"),
            Map.entry("建筑", "building architecture house"),
            Map.entry("旅行", "travel journey"),
            Map.entry("山", "mountain hill"),
            Map.entry("花", "flower floral"),
            Map.entry("树", "tree forest")
    );

    public Page<PhotoDTO> search(SearchRequest request, Long userId) {
        PageRequest pageRequest = PageRequest.of(request.getPage(), request.getSize());

        if ("semantic".equals(request.getType())) {
            String thresholdStr = settingService.getSetting(userId, "ai_search_similarity_threshold");
            double threshold = thresholdStr != null ? Double.parseDouble(thresholdStr) / 100.0 : 0.8;
            return semanticSearch(request.getQuery(), threshold, pageRequest);
        } else {
            return fullTextSearch(request.getQuery(), pageRequest);
        }
    }

    private Page<PhotoDTO> fullTextSearch(String query, PageRequest pageRequest) {
        return photoRepository.fullTextSearch(query, pageRequest).map(this::toDTO);
    }

    private Page<PhotoDTO> semanticSearch(String query, Double threshold, PageRequest pageRequest) {
        log.info("Semantic search for: {} (threshold: {})", query, threshold);

        // Translate Chinese to English for better CLIP matching
        String searchQuery = ZH_TO_EN.getOrDefault(query, query);

        AiServiceClient.EmbeddingResponse resp = aiServiceClient.embedText(searchQuery);
        String vectorStr = vectorToString(resp.getEmbedding());
        List<Photo> results = photoRepository.findByVectorSimilarity(vectorStr, threshold, 100);

        // Sort by distance (most similar first)
        results.sort((a, b) -> {
            double distA = cosineDistance(a.getEmbedding(), resp.getEmbedding());
            double distB = cosineDistance(b.getEmbedding(), resp.getEmbedding());
            return Double.compare(distA, distB);
        });

        int start = (int) pageRequest.getOffset();
        int end = Math.min(start + pageRequest.getPageSize(), results.size());
        List<PhotoDTO> page = results.subList(start, end).stream().map(this::toDTO).toList();
        return new PageImpl<>(page, pageRequest, results.size());
    }

    private double cosineDistance(float[] a, List<Float> b) {
        if (a == null || b == null || a.length != b.size()) return 1.0;
        float dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b.get(i);
        }
        return 1.0 - dot;
    }

    private String vectorToString(List<Float> vector) {
        return "[" + vector.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")) + "]";
    }

    private PhotoDTO toDTO(Photo photo) {
        PhotoDTO dto = new PhotoDTO();
        dto.setId(photo.getId());
        dto.setFilePath(photo.getFilePath());
        dto.setExifDate(photo.getExifDate());
        dto.setGpsLat(photo.getGpsLat());
        dto.setGpsLng(photo.getGpsLng());
        dto.setRating(photo.getRating());
        dto.setNote(photo.getNote());
        dto.setAiCaption(photo.getAiCaption());
        dto.setWidth(photo.getWidth());
        dto.setHeight(photo.getHeight());
        dto.setFileSize(photo.getFileSize());
        dto.setMediaType(photo.getMediaType().name());
        dto.setFavorite(photo.getFavorite());
        dto.setCreatedAt(photo.getCreatedAt());
        try {
            String thumbExt = photo.getOriginalFilename() != null
                    && photo.getOriginalFilename().toLowerCase().endsWith(".webp") ? "webp" : "jpg";
            dto.setThumbnailUrl(storageService.getThumbnailUrl(photo.getFileHashMd5() + "/thumb." + thumbExt));
        } catch (Exception e) {
            log.warn("Failed to generate thumbnail URL for photo {}", photo.getId());
        }
        return dto;
    }
}
