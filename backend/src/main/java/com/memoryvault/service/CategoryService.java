package com.memoryvault.service;

import com.memoryvault.dto.CategoryDTO;
import com.memoryvault.dto.PhotoDTO;
import com.memoryvault.entity.Category;
import com.memoryvault.entity.Photo;
import com.memoryvault.entity.PhotoTag;
import com.memoryvault.entity.Tag;
import com.memoryvault.repository.CategoryRepository;
import com.memoryvault.repository.PhotoRepository;
import com.memoryvault.repository.PhotoTagRepository;
import com.memoryvault.repository.TagRepository;
import com.memoryvault.storage.MinioStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final PhotoRepository photoRepository;
    private final PhotoTagRepository photoTagRepository;
    private final TagRepository tagRepository;
    private final MinioStorageService storageService;

    public List<CategoryDTO> listCategories() {
        return categoryRepository.findAll().stream().map(this::toDTO).toList();
    }

    public CategoryDTO getCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));
        return toDTO(category);
    }

    public Page<PhotoDTO> getCategoryPhotos(Long id, Pageable pageable) {
        // Verify category exists
        categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));
        return categoryRepository.findCategoryPhotos(id, pageable).map(this::toPhotoDTO);
    }

    @Transactional
    public CategoryDTO createCategory(CategoryDTO dto) {
        Category category = new Category();
        category.setName(dto.getName());
        category.setIcon(dto.getIcon());
        category.setColor(dto.getColor());
        category.setIsSystem(false);
        category = categoryRepository.save(category);
        return toDTO(category);
    }

    @Transactional
    public CategoryDTO updateCategory(Long id, CategoryDTO dto) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));
        if (dto.getName() != null) category.setName(dto.getName());
        if (dto.getIcon() != null) category.setIcon(dto.getIcon());
        if (dto.getColor() != null) category.setColor(dto.getColor());
        category = categoryRepository.save(category);
        return toDTO(category);
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));
        if (category.getIsSystem()) {
            throw new RuntimeException("Cannot delete system category");
        }
        categoryRepository.deleteById(id);
    }

    @Transactional
    public CategoryDTO trainCategory(Long id, List<Long> photoIds, Double threshold) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        // Set threshold
        if (threshold != null) {
            category.setThreshold(threshold);
        }

        // Fetch template photos
        List<Photo> templatePhotos = new ArrayList<>();
        for (Long photoId : photoIds) {
            photoRepository.findById(photoId).ifPresent(templatePhotos::add);
        }

        if (templatePhotos.isEmpty()) {
            throw new RuntimeException("No valid template photos found");
        }

        // Collect embeddings from template photos
        List<float[]> embeddings = new ArrayList<>();
        for (Photo photo : templatePhotos) {
            if (photo.getEmbedding() != null) {
                embeddings.add(photo.getEmbedding());
            }
        }

        if (embeddings.isEmpty()) {
            throw new RuntimeException("Template photos have no embeddings. Please wait for AI indexing to complete.");
        }

        // Compute centroid (average of embeddings)
        int dim = embeddings.get(0).length;
        float[] centroid = new float[dim];
        for (float[] emb : embeddings) {
            for (int j = 0; j < dim; j++) {
                centroid[j] += emb[j];
            }
        }
        for (int j = 0; j < dim; j++) {
            centroid[j] /= embeddings.size();
        }

        // L2-normalize the centroid
        float norm = 0;
        for (float v : centroid) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int j = 0; j < dim; j++) {
                centroid[j] /= norm;
            }
        }

        // Save prototype vector
        category.setPrototypeVector(centroid);

        // Clear existing auto-assigned photos (keep manual ones)
        categoryRepository.clearAutoAssignedPhotos(id);

        // Convert centroid to pgvector string format
        StringBuilder vectorStr = new StringBuilder("[");
        for (int j = 0; j < centroid.length; j++) {
            if (j > 0) vectorStr.append(",");
            vectorStr.append(centroid[j]);
        }
        vectorStr.append("]");

        // Bulk assign similar photos using pgvector
        double distanceThreshold = 1.0 - category.getThreshold();
        int assigned = categoryRepository.bulkAssignPhotos(id, vectorStr.toString(), distanceThreshold);
        log.info("Category '{}' training complete: {} photos auto-assigned", category.getName(), assigned);

        // Set cover photo to first template photo if not set
        if (category.getCoverPhoto() == null && !templatePhotos.isEmpty()) {
            category.setCoverPhoto(templatePhotos.get(0));
        }

        // Update photo count
        categoryRepository.updatePhotoCount(id);

        // Reload to get accurate count
        category = categoryRepository.findById(id).orElse(category);
        return toDTO(category);
    }

    @Transactional
    public void addPhotoToCategory(Long categoryId, Long photoId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Category not found"));
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new RuntimeException("Photo not found"));
        if (!category.getPhotos().contains(photo)) {
            category.getPhotos().add(photo);
            categoryRepository.save(category);
            categoryRepository.updatePhotoCount(categoryId);
        }
    }

    @Transactional
    public void removePhotoFromCategory(Long categoryId, Long photoId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Category not found"));
        category.getPhotos().removeIf(p -> p.getId().equals(photoId));
        categoryRepository.save(category);
        categoryRepository.updatePhotoCount(categoryId);
    }

    /**
     * Reclassify all existing photos into system categories based on their YOLO tags.
     * YOLO category -> system category icon mapping.
     */
    private static final Map<String, String> YOLO_TO_CATEGORY_ICON = Map.of(
            "person", "person",
            "animal", "animal",
            "food", "food"
    );
    private static final Map<String, String> YOLO_NAME_OVERRIDE = Map.of(
            "potted plant", "plant"
    );

    @Transactional
    public int reclassifyAll() {
        List<Category> systemCategories = categoryRepository.findByIsSystemTrue();
        if (systemCategories.isEmpty()) return 0;

        // Build a map of icon -> category for quick lookup
        java.util.Map<String, Category> iconToCategory = new java.util.HashMap<>();
        for (Category cat : systemCategories) {
            iconToCategory.put(cat.getIcon(), cat);
        }

        // Find all AI-generated tags
        List<Tag> aiTags = tagRepository.findByType(Tag.TagType.AI);
        int assigned = 0;

        for (Tag tag : aiTags) {
            // Determine which system category this tag matches
            String matchedIcon = YOLO_NAME_OVERRIDE.get(tag.getName());
            if (matchedIcon == null) {
                matchedIcon = YOLO_TO_CATEGORY_ICON.get(tag.getName());
            }
            if (matchedIcon == null) continue;

            Category category = iconToCategory.get(matchedIcon);
            if (category == null) continue;

            // Find all photos with this tag
            List<PhotoTag> photoTags = photoTagRepository.findByTagId(tag.getId());
            for (PhotoTag pt : photoTags) {
                Photo photo = photoRepository.findById(pt.getPhotoId()).orElse(null);
                if (photo != null && !category.getPhotos().contains(photo)) {
                    category.getPhotos().add(photo);
                    assigned++;
                }
            }
            categoryRepository.save(category);
            categoryRepository.updatePhotoCount(category.getId());
        }

        log.info("Reclassified {} photo-category assignments", assigned);
        return assigned;
    }

    private CategoryDTO toDTO(Category category) {
        CategoryDTO dto = new CategoryDTO();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setIcon(category.getIcon());
        dto.setColor(category.getColor());
        dto.setIsSystem(category.getIsSystem());
        dto.setPhotoCount(category.getPhotoCount());
        dto.setTrained(category.getPrototypeVector() != null);
        dto.setCreatedAt(category.getCreatedAt());
        if (category.getCoverPhoto() != null) {
            dto.setCoverPhotoId(category.getCoverPhoto().getId());
            try {
                dto.setCoverPhotoUrl(storageService.getThumbnailUrl(
                        category.getCoverPhoto().getFileHashMd5() + "/thumb.jpg"));
            } catch (Exception e) {
                log.warn("Failed to resolve cover photo URL for category {}", category.getId());
            }
        }
        return dto;
    }

    private PhotoDTO toPhotoDTO(Photo photo) {
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
        dto.setOriginalFilename(photo.getOriginalFilename());
        dto.setCreatedAt(photo.getCreatedAt());
        try {
            String thumbExt = photo.getOriginalFilename() != null
                    && photo.getOriginalFilename().toLowerCase().endsWith(".webp") ? "webp" : "jpg";
            dto.setThumbnailUrl(storageService.getThumbnailUrl(
                    photo.getFileHashMd5() + "/thumb." + thumbExt));
            dto.setOriginalUrl(storageService.getPhotoUrl(photo.getFilePath()));
        } catch (Exception e) {
            log.warn("Failed to resolve URLs for photo {}", photo.getId());
        }
        return dto;
    }
}
