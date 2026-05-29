package com.memoryvault.controller;

import com.memoryvault.dto.PhotoDTO;
import com.memoryvault.entity.User;
import com.memoryvault.repository.UserRepository;
import com.memoryvault.service.PhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/photos")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photoService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<Page<PhotoDTO>> listPhotos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(photoService.listPhotos(PageRequest.of(page, size)));
    }

    @PostMapping("/upload")
    public ResponseEntity<PhotoDTO> uploadPhoto(@RequestParam("file") MultipartFile file, Authentication authentication) throws Exception {
        Long userId = getUserId(authentication);
        return ResponseEntity.ok(photoService.uploadPhoto(file, userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PhotoDTO> getPhoto(@PathVariable Long id) {
        return ResponseEntity.ok(photoService.getPhoto(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PhotoDTO> updatePhoto(@PathVariable Long id, @RequestBody PhotoDTO updates) {
        return ResponseEntity.ok(photoService.updatePhoto(id, updates));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePhoto(@PathVariable Long id) {
        photoService.deletePhoto(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/favorites")
    public ResponseEntity<Page<PhotoDTO>> getFavorites(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(photoService.getFavorites(PageRequest.of(page, size)));
    }

    @GetMapping("/rated")
    public ResponseEntity<Page<PhotoDTO>> getByRating(
            @RequestParam(defaultValue = "3") int minRating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(photoService.getByRating(minRating, PageRequest.of(page, size)));
    }

    private Long getUserId(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getId();
    }
}
