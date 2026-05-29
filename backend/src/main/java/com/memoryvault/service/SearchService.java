package com.memoryvault.service;

import com.memoryvault.ai.AiServiceClient;
import com.memoryvault.dto.PhotoDTO;
import com.memoryvault.dto.SearchRequest;
import com.memoryvault.entity.Photo;
import com.memoryvault.repository.PhotoRepository;
import com.memoryvault.storage.MinioStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final PhotoRepository photoRepository;
    private final MinioStorageService storageService;
    private final AiServiceClient aiServiceClient;

    public Page<PhotoDTO> search(SearchRequest request) {
        PageRequest pageRequest = PageRequest.of(request.getPage(), request.getSize());

        if ("semantic".equals(request.getType())) {
            return semanticSearch(request.getQuery(), 0.75, pageRequest);
        } else {
            return fullTextSearch(request.getQuery(), pageRequest);
        }
    }

    private Page<PhotoDTO> fullTextSearch(String query, PageRequest pageRequest) {
        return photoRepository.fullTextSearch(query, pageRequest).map(this::toDTO);
    }

    private Page<PhotoDTO> semanticSearch(String query, Double threshold, PageRequest pageRequest) {
        log.info("Semantic search for: {} (threshold: {})", query, threshold);
        AiServiceClient.EmbeddingResponse resp = aiServiceClient.embedText(query);
        String vectorStr = vectorToString(resp.getEmbedding());
        List<Photo> results = photoRepository.findByVectorSimilarity(vectorStr, threshold, 100);
        int start = (int) pageRequest.getOffset();
        int end = Math.min(start + pageRequest.getPageSize(), results.size());
        List<PhotoDTO> page = results.subList(start, end).stream().map(this::toDTO).toList();
        return new PageImpl<>(page, pageRequest, results.size());
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
