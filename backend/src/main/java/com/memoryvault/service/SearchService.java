package com.memoryvault.service;

import com.memoryvault.dto.PhotoDTO;
import com.memoryvault.dto.SearchRequest;
import com.memoryvault.entity.Photo;
import com.memoryvault.repository.PhotoRepository;
import com.memoryvault.storage.MinioStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final PhotoRepository photoRepository;
    private final MinioStorageService storageService;

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
        // This would call the AI service to get the embedding for the query,
        // then search using pgvector cosine similarity.
        // For now, fall back to full text search.
        log.info("Semantic search for: {} (threshold: {})", query, threshold);
        return fullTextSearch(query, pageRequest);
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
            dto.setThumbnailUrl(storageService.getThumbnailUrl(photo.getFileHashMd5() + "/thumb.webp"));
        } catch (Exception e) {
            log.warn("Failed to generate thumbnail URL for photo {}", photo.getId());
        }
        return dto;
    }
}
