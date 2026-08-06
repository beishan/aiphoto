package com.memoryvault.controller;

import com.memoryvault.dto.PhotoDTO;
import com.memoryvault.dto.TagDTO;
import com.memoryvault.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping
    public ResponseEntity<List<TagDTO>> listTags(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy) {
        return ResponseEntity.ok(tagService.listTags(search, sortBy));
    }

    @PostMapping
    public ResponseEntity<TagDTO> createTag(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        String color = request.get("color");
        String description = request.get("description");
        return ResponseEntity.ok(tagService.createTag(name, color, description));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TagDTO> updateTag(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        String name = (String) request.get("name");
        String color = (String) request.get("color");
        String description = (String) request.get("description");
        Integer sortOrder = request.get("sortOrder") != null
                ? ((Number) request.get("sortOrder")).intValue() : null;
        return ResponseEntity.ok(tagService.updateTag(id, name, color, description, sortOrder));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTag(@PathVariable Long id) {
        tagService.deleteTag(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/merge")
    public ResponseEntity<Void> mergeTags(@RequestBody Map<String, Long> request) {
        tagService.mergeTags(request.get("sourceId"), request.get("targetId"));
        return ResponseEntity.ok().build();
    }

    // ===== Tag photos =====

    @GetMapping("/{id}/photos")
    public ResponseEntity<List<PhotoDTO>> getTagPhotos(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(tagService.getPhotosByTag(id, page, size));
    }

    @GetMapping("/{id}/cover")
    public ResponseEntity<List<PhotoDTO>> getTagCoverPhotos(
            @PathVariable Long id,
            @RequestParam(defaultValue = "4") int limit) {
        return ResponseEntity.ok(tagService.getTagCoverPhotos(id, limit));
    }

    @PostMapping("/photos/{photoId}")
    public ResponseEntity<Void> addTagToPhoto(
            @PathVariable Long photoId,
            @RequestBody Map<String, Object> request) {
        if (request.get("tagId") != null) {
            Long tagId = ((Number) request.get("tagId")).longValue();
            tagService.addTagToPhoto(photoId, tagId);
        } else if (request.get("tagName") != null) {
            String tagName = (String) request.get("tagName");
            tagService.addTagToPhotoByName(photoId, tagName);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/photos/{photoId}/{tagId}")
    public ResponseEntity<Void> removeTagFromPhoto(
            @PathVariable Long photoId,
            @PathVariable Long tagId) {
        tagService.removeTagFromPhoto(photoId, tagId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/batch-add")
    public ResponseEntity<Void> batchAddTags(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Long> photoIds = ((List<Number>) request.get("photoIds")).stream().map(Number::longValue).toList();
        Long tagId = ((Number) request.get("tagId")).longValue();
        tagService.batchAddTags(photoIds, tagId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/batch-remove")
    public ResponseEntity<Void> batchRemoveTags(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Long> photoIds = ((List<Number>) request.get("photoIds")).stream().map(Number::longValue).toList();
        Long tagId = ((Number) request.get("tagId")).longValue();
        tagService.batchRemoveTags(photoIds, tagId);
        return ResponseEntity.ok().build();
    }
}
