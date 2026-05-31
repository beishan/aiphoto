package com.memoryvault.controller;

import com.memoryvault.dto.AlbumDTO;
import com.memoryvault.dto.PhotoDTO;
import com.memoryvault.service.AlbumService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/albums")
@RequiredArgsConstructor
public class AlbumController {

    private final AlbumService albumService;

    @GetMapping
    public ResponseEntity<List<AlbumDTO>> listAlbums() {
        return ResponseEntity.ok(albumService.listAlbums());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlbumDTO> getAlbum(@PathVariable Long id) {
        return ResponseEntity.ok(albumService.getAlbum(id));
    }

    @GetMapping("/{id}/photos")
    public ResponseEntity<List<PhotoDTO>> getAlbumPhotos(@PathVariable Long id) {
        return ResponseEntity.ok(albumService.getAlbumPhotos(id));
    }

    @PostMapping
    public ResponseEntity<AlbumDTO> createAlbum(@RequestBody AlbumDTO albumDTO) {
        return ResponseEntity.ok(albumService.createAlbum(albumDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AlbumDTO> updateAlbum(@PathVariable Long id, @RequestBody AlbumDTO albumDTO) {
        return ResponseEntity.ok(albumService.updateAlbum(id, albumDTO));
    }

    @PutMapping("/{id}/cover")
    public ResponseEntity<AlbumDTO> setCoverPhoto(
            @PathVariable Long id,
            @RequestBody Map<String, Long> request) {
        Long photoId = request.get("photoId");
        return ResponseEntity.ok(albumService.setCoverPhoto(id, photoId));
    }

    @PostMapping("/{albumId}/photos/{photoId}")
    public ResponseEntity<Void> addPhotoToAlbum(@PathVariable Long albumId, @PathVariable Long photoId) {
        albumService.addPhotoToAlbum(albumId, photoId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{albumId}/photos/{photoId}")
    public ResponseEntity<Void> removePhotoFromAlbum(@PathVariable Long albumId, @PathVariable Long photoId) {
        albumService.removePhotoFromAlbum(albumId, photoId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAlbum(@PathVariable Long id) {
        albumService.deleteAlbum(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/train")
    public ResponseEntity<Map<String, Object>> trainAlbum(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0.75") Double threshold) {
        return ResponseEntity.ok(albumService.trainAlbum(id, threshold));
    }
}
