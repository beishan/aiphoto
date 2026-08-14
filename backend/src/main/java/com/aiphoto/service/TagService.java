package com.aiphoto.service;

import com.aiphoto.dto.PhotoDTO;
import com.aiphoto.dto.TagDTO;
import com.aiphoto.entity.Photo;
import com.aiphoto.entity.PhotoTag;
import com.aiphoto.entity.PhotoTagId;
import com.aiphoto.entity.Tag;
import com.aiphoto.repository.PhotoRepository;
import com.aiphoto.repository.PhotoTagRepository;
import com.aiphoto.repository.TagRepository;
import com.aiphoto.storage.LocalStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;
    private final PhotoTagRepository photoTagRepository;
    private final PhotoRepository photoRepository;
    private final LocalStorageService storageService;

    // ===== Tag CRUD =====

    public List<TagDTO> listTags(String search, String sortBy) {
        List<Tag> tags;
        if (search != null && !search.isBlank()) {
            tags = tagRepository.findByNameContainingIgnoreCase(search);
        } else {
            tags = tagRepository.findAllByOrderBySortOrderAscCreatedAtDesc();
        }

        List<TagDTO> dtos = tags.stream().map(t -> {
            TagDTO dto = toDTO(t);
            dto.setPhotoCount((int) tagRepository.countPhotosByTagId(t.getId()));
            return dto;
        }).collect(Collectors.toList());

        // Sort
        if ("name".equals(sortBy)) {
            dtos.sort(Comparator.comparing(TagDTO::getName, String.CASE_INSENSITIVE_ORDER));
        } else if ("count".equals(sortBy)) {
            dtos.sort(Comparator.comparing(TagDTO::getPhotoCount).reversed());
        }

        return dtos;
    }

    @Transactional
    public TagDTO createTag(String name, String color, String description) {
        if (tagRepository.findByName(name).isPresent()) {
            throw new RuntimeException("标签名称已存在");
        }
        Tag tag = new Tag();
        tag.setName(name);
        tag.setColor(color != null ? color : "#0a84ff");
        tag.setDescription(description);
        tag.setType(Tag.TagType.MANUAL);
        tag = tagRepository.save(tag);
        return toDTO(tag);
    }

    @Transactional
    public TagDTO updateTag(Long id, String name, String color, String description, Integer sortOrder) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("标签不存在"));

        if (name != null && !name.equals(tag.getName())) {
            if (tagRepository.findByName(name).isPresent()) {
                throw new RuntimeException("标签名称已存在");
            }
            tag.setName(name);
        }
        if (color != null) tag.setColor(color);
        if (description != null) tag.setDescription(description);
        if (sortOrder != null) tag.setSortOrder(sortOrder);

        tag = tagRepository.save(tag);
        return toDTO(tag);
    }

    @Transactional
    public void deleteTag(Long id) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("标签不存在"));
        // Remove all photo-tag associations
        photoTagRepository.deleteByTagId(id);
        tagRepository.deleteById(id);
    }

    @Transactional
    public void mergeTags(Long sourceId, Long targetId) {
        Tag source = tagRepository.findById(sourceId)
                .orElseThrow(() -> new RuntimeException("源标签不存在"));
        Tag target = tagRepository.findById(targetId)
                .orElseThrow(() -> new RuntimeException("目标标签不存在"));

        // Get all photo IDs for source tag
        List<PhotoTag> sourcePhotoTags = photoTagRepository.findByTagId(sourceId);
        for (PhotoTag pt : sourcePhotoTags) {
            PhotoTagId newId = new PhotoTagId();
            newId.setPhotoId(pt.getPhotoId());
            newId.setTagId(targetId);
            if (!photoTagRepository.existsById(newId)) {
                PhotoTag newPt = new PhotoTag();
                newPt.setPhotoId(pt.getPhotoId());
                newPt.setTagId(targetId);
                newPt.setSource(pt.getSource());
                newPt.setConfidence(pt.getConfidence());
                photoTagRepository.save(newPt);
            }
        }

        // Delete source tag
        photoTagRepository.deleteByTagId(sourceId);
        tagRepository.deleteById(sourceId);
    }

    // ===== Photo-Tag Operations =====

    @Transactional
    public void addTagToPhoto(Long photoId, Long tagId) {
        PhotoTagId id = new PhotoTagId();
        id.setPhotoId(photoId);
        id.setTagId(tagId);
        if (photoTagRepository.existsById(id)) return;

        PhotoTag pt = new PhotoTag();
        pt.setPhotoId(photoId);
        pt.setTagId(tagId);
        pt.setSource(Tag.TagType.MANUAL);
        pt.setConfidence(1.0);
        photoTagRepository.save(pt);
    }

    @Transactional
    public void addTagToPhotoByName(Long photoId, String tagName) {
        Tag tag = tagRepository.findByName(tagName)
                .orElseGet(() -> {
                    Tag t = new Tag();
                    t.setName(tagName);
                    t.setColor("#0a84ff");
                    t.setType(Tag.TagType.MANUAL);
                    return tagRepository.save(t);
                });
        addTagToPhoto(photoId, tag.getId());
    }

    @Transactional
    public void removeTagFromPhoto(Long photoId, Long tagId) {
        PhotoTagId id = new PhotoTagId();
        id.setPhotoId(photoId);
        id.setTagId(tagId);
        photoTagRepository.deleteById(id);
    }

    @Transactional
    public void batchAddTags(List<Long> photoIds, Long tagId) {
        for (Long photoId : photoIds) {
            addTagToPhoto(photoId, tagId);
        }
    }

    @Transactional
    public void batchRemoveTags(List<Long> photoIds, Long tagId) {
        for (Long photoId : photoIds) {
            removeTagFromPhoto(photoId, tagId);
        }
    }

    // ===== Tag detail with photos =====

    public List<PhotoDTO> getPhotosByTag(Long tagId, int page, int size) {
        List<Long> photoIds = photoTagRepository.findPhotoIdsByTagId(tagId);

        int start = page * size;
        int end = Math.min(start + size, photoIds.size());
        if (start >= photoIds.size()) return List.of();

        List<Long> pageIds = photoIds.subList(start, end);
        List<Photo> photos = photoRepository.findAllById(pageIds);

        // Maintain original order
        Map<Long, Photo> photoMap = photos.stream()
                .collect(Collectors.toMap(Photo::getId, p -> p));

        return pageIds.stream()
                .map(photoMap::get)
                .filter(Objects::nonNull)
                .map(this::toPhotoDTO)
                .collect(Collectors.toList());
    }

    public List<PhotoDTO> getTagCoverPhotos(Long tagId, int limit) {
        List<Long> photoIds = photoTagRepository.findPhotoIdsByTagId(tagId);
        if (photoIds.isEmpty()) return List.of();

        int end = Math.min(limit, photoIds.size());
        List<Long> coverIds = photoIds.subList(0, end);
        List<Photo> photos = photoRepository.findAllById(coverIds);

        Map<Long, Photo> photoMap = photos.stream()
                .collect(Collectors.toMap(Photo::getId, p -> p));

        return coverIds.stream()
                .map(photoMap::get)
                .filter(Objects::nonNull)
                .map(this::toPhotoDTO)
                .collect(Collectors.toList());
    }

    private TagDTO toDTO(Tag tag) {
        TagDTO dto = new TagDTO();
        dto.setId(tag.getId());
        dto.setName(tag.getName());
        dto.setColor(tag.getColor());
        dto.setType(tag.getType().name());
        dto.setCategory(tag.getCategory());
        dto.setDescription(tag.getDescription());
        dto.setSortOrder(tag.getSortOrder());
        dto.setCreatedAt(tag.getCreatedAt());
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
        dto.setInTimeline(photo.getInTimeline());
        dto.setOriginalFilename(photo.getOriginalFilename());
        dto.setCreatedAt(photo.getCreatedAt());
        dto.setSourceFolderId(photo.getSourceFolderId());

        try {
            String thumbExt = isWebPFilename(photo.getOriginalFilename()) ? "webp" : "jpg";
            dto.setThumbnailUrl(storageService.getThumbnailUrl(photo.getFileHashMd5() + "/thumb." + thumbExt));
            dto.setOriginalUrl(storageService.getPhotoUrl(photo.getFilePath()));
        } catch (Exception e) {
            log.warn("Failed to generate URL for photo {}", photo.getId());
        }
        return dto;
    }

    private boolean isWebPFilename(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".webp");
    }
}
