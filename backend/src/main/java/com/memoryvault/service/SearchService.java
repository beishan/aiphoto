package com.memoryvault.service;

import com.memoryvault.ai.AiServiceClient;
import com.memoryvault.dto.PhotoDTO;
import com.memoryvault.dto.SearchRequest;
import com.memoryvault.entity.Photo;
import com.memoryvault.repository.PhotoRepository;
import com.memoryvault.repository.PhotoTagRepository;
import com.memoryvault.storage.LocalStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final PhotoRepository photoRepository;
    private final PhotoTagRepository photoTagRepository;
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

        // Check if there are filter conditions
        boolean hasFilters = request.getTagId() != null
                || request.getMinRating() != null
                || request.getMaxRating() != null
                || request.getHasDescription() != null
                || request.getInTimeline() != null
                || request.getFolderId() != null
                || request.getFileType() != null
                || request.getStartDate() != null
                || request.getEndDate() != null
                || request.getPersonId() != null;

        // If no query and has filters, use filtered search
        if ((request.getQuery() == null || request.getQuery().isBlank()) && hasFilters) {
            return filteredSearch(request, pageRequest);
        }

        // If has query, use text or semantic search, then apply filters
        if ("semantic".equals(request.getType()) && request.getQuery() != null && !request.getQuery().isBlank()) {
            String thresholdStr = settingService.getSetting(userId, "ai_search_similarity_threshold");
            double threshold = thresholdStr != null ? Double.parseDouble(thresholdStr) / 100.0 : 0.8;
            return semanticSearch(request, threshold, pageRequest, hasFilters);
        } else if (request.getQuery() != null && !request.getQuery().isBlank()) {
            return fullTextSearch(request, pageRequest, hasFilters);
        } else {
            // No query and no filters - return all
            return filteredSearch(request, pageRequest);
        }
    }

    private Page<PhotoDTO> fullTextSearch(SearchRequest request, PageRequest pageRequest, boolean hasFilters) {
        List<Photo> results = photoRepository.fullTextSearch(request.getQuery(), PageRequest.of(0, 1000)).getContent();
        List<Photo> filtered = applyFilters(results, request);
        return paginate(filtered, pageRequest);
    }

    private Page<PhotoDTO> semanticSearch(SearchRequest request, Double threshold, PageRequest pageRequest, boolean hasFilters) {
        log.info("Semantic search for: {} (threshold: {})", request.getQuery(), threshold);

        String searchQuery = ZH_TO_EN.getOrDefault(request.getQuery(), request.getQuery());

        AiServiceClient.EmbeddingResponse resp = aiServiceClient.embedText(searchQuery);
        String vectorStr = vectorToString(resp.getEmbedding());
        List<Photo> results = photoRepository.findByVectorSimilarity(vectorStr, threshold, 200);

        results.sort((a, b) -> {
            double distA = cosineDistance(a.getEmbedding(), resp.getEmbedding());
            double distB = cosineDistance(b.getEmbedding(), resp.getEmbedding());
            return Double.compare(distA, distB);
        });

        if (hasFilters) {
            results = applyFilters(results, request);
        }

        return paginate(results, pageRequest);
    }

    private Page<PhotoDTO> filteredSearch(SearchRequest request, PageRequest pageRequest) {
        List<Photo> allPhotos = photoRepository.findAll();
        List<Photo> filtered = applyFilters(allPhotos, request);

        // Sort
        String sortBy = request.getSortBy();
        String sortOrder = request.getSortOrder();
        Comparator<Photo> comparator;
        if ("rating".equals(sortBy)) {
            comparator = Comparator.comparing(p -> p.getRating() != null ? p.getRating() : 0);
        } else if ("name".equals(sortBy)) {
            comparator = Comparator.comparing(p -> p.getOriginalFilename() != null ? p.getOriginalFilename() : "");
        } else {
            comparator = Comparator.comparing(p -> p.getExifDate() != null ? p.getExifDate() : p.getCreatedAt());
        }
        if ("desc".equals(sortOrder)) {
            comparator = comparator.reversed();
        }
        filtered.sort(comparator);

        return paginate(filtered, pageRequest);
    }

    private List<Photo> applyFilters(List<Photo> photos, SearchRequest request) {
        Stream<Photo> stream = photos.stream();

        // Date range filter
        if (request.getStartDate() != null && !request.getStartDate().isBlank()) {
            try {
                LocalDateTime start = java.sql.Timestamp.valueOf(request.getStartDate() + " 00:00:00").toLocalDateTime();
                stream = stream.filter(p -> p.getExifDate() != null && !p.getExifDate().isBefore(start));
            } catch (Exception e) {
                log.warn("Invalid startDate: {}", request.getStartDate());
            }
        }
        if (request.getEndDate() != null && !request.getEndDate().isBlank()) {
            try {
                LocalDateTime end = java.sql.Timestamp.valueOf(request.getEndDate() + " 23:59:59").toLocalDateTime();
                stream = stream.filter(p -> p.getExifDate() != null && !p.getExifDate().isAfter(end));
            } catch (Exception e) {
                log.warn("Invalid endDate: {}", request.getEndDate());
            }
        }

        // Rating filter
        if (request.getMinRating() != null) {
            stream = stream.filter(p -> p.getRating() != null && p.getRating() >= request.getMinRating());
        }
        if (request.getMaxRating() != null) {
            stream = stream.filter(p -> p.getRating() != null && p.getRating() <= request.getMaxRating());
        }

        // Has description filter
        if (request.getHasDescription() != null) {
            if (request.getHasDescription()) {
                stream = stream.filter(p -> p.getNote() != null && !p.getNote().isBlank());
            } else {
                stream = stream.filter(p -> p.getNote() == null || p.getNote().isBlank());
            }
        }

        // In timeline filter
        if (request.getInTimeline() != null) {
            stream = stream.filter(p -> p.getInTimeline() != null && p.getInTimeline().equals(request.getInTimeline()));
        }

        // File type filter
        if (request.getFileType() != null && !request.getFileType().isBlank()) {
            stream = stream.filter(p -> p.getMediaType() != null && p.getMediaType().name().equals(request.getFileType()));
        }

        // Folder filter
        if (request.getFolderId() != null) {
            stream = stream.filter(p -> request.getFolderId().equals(p.getSourceFolderId()));
        }

        // Tag filter
        if (request.getTagId() != null) {
            List<Long> tagPhotoIds = photoTagRepository.findPhotoIdsByTagId(request.getTagId());
            Set<Long> tagPhotoIdSet = new HashSet<>(tagPhotoIds);
            stream = stream.filter(p -> tagPhotoIdSet.contains(p.getId()));
        }

        return stream.collect(Collectors.toList());
    }

    private Page<PhotoDTO> paginate(List<Photo> photos, PageRequest pageRequest) {
        int start = (int) pageRequest.getOffset();
        int end = Math.min(start + pageRequest.getPageSize(), photos.size());
        if (start >= photos.size()) {
            return new PageImpl<>(List.of(), pageRequest, photos.size());
        }
        List<PhotoDTO> page = photos.subList(start, end).stream().map(this::toDTO).toList();
        return new PageImpl<>(page, pageRequest, photos.size());
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
        dto.setInTimeline(photo.getInTimeline());
        dto.setOriginalFilename(photo.getOriginalFilename());
        dto.setCreatedAt(photo.getCreatedAt());
        dto.setSourceFolderId(photo.getSourceFolderId());
        try {
            String thumbExt = photo.getOriginalFilename() != null
                    && photo.getOriginalFilename().toLowerCase().endsWith(".webp") ? "webp" : "jpg";
            dto.setThumbnailUrl(storageService.getThumbnailUrl(photo.getFileHashMd5() + "/thumb." + thumbExt));
            dto.setOriginalUrl(storageService.getPhotoUrl(photo.getFilePath()));
        } catch (Exception e) {
            log.warn("Failed to generate thumbnail URL for photo {}", photo.getId());
        }
        return dto;
    }
}
