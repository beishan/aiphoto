package com.memoryvault.controller;

import com.memoryvault.dto.CategoryDTO;
import com.memoryvault.dto.PhotoDTO;
import com.memoryvault.service.CategoryService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<CategoryDTO>> listCategories() {
        return ResponseEntity.ok(categoryService.listCategories());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryDTO> getCategory(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getCategory(id));
    }

    @GetMapping("/{id}/photos")
    public ResponseEntity<Page<PhotoDTO>> getCategoryPhotos(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "40") int size) {
        return ResponseEntity.ok(categoryService.getCategoryPhotos(id,
                org.springframework.data.domain.PageRequest.of(page, size)));
    }

    @PostMapping
    public ResponseEntity<CategoryDTO> createCategory(@RequestBody CategoryDTO dto) {
        return ResponseEntity.ok(categoryService.createCategory(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryDTO> updateCategory(@PathVariable Long id, @RequestBody CategoryDTO dto) {
        return ResponseEntity.ok(categoryService.updateCategory(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/train")
    public ResponseEntity<Map<String, Object>> trainCategory(
            @PathVariable Long id,
            @RequestBody TrainRequest request) {
        CategoryDTO result = categoryService.trainCategory(id, request.getPhotoIds(), request.getThreshold());
        return ResponseEntity.ok(Map.of(
                "category", result,
                "message", "Training complete. " + result.getPhotoCount() + " photos assigned."
        ));
    }

    @PostMapping("/{id}/photos/{photoId}")
    public ResponseEntity<Void> addPhoto(@PathVariable Long id, @PathVariable Long photoId) {
        categoryService.addPhotoToCategory(id, photoId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    public ResponseEntity<Void> removePhoto(@PathVariable Long id, @PathVariable Long photoId) {
        categoryService.removePhotoFromCategory(id, photoId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reclassify")
    public ResponseEntity<Map<String, Object>> reclassifyAll() {
        int assigned = categoryService.reclassifyAll();
        return ResponseEntity.ok(Map.of(
                "assigned", assigned,
                "message", "已将 " + assigned + " 张照片分配到对应分类"
        ));
    }

    @Data
    public static class TrainRequest {
        private List<Long> photoIds;
        private Double threshold;
    }
}
