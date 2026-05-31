package com.memoryvault.controller;

import com.memoryvault.dto.PhotoDTO;
import com.memoryvault.dto.ScanFolderDTO;
import com.memoryvault.service.FolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    @GetMapping
    public ResponseEntity<List<ScanFolderDTO>> listFolders() {
        return ResponseEntity.ok(folderService.listFolders());
    }

    @PostMapping
    public ResponseEntity<ScanFolderDTO> addFolder(@RequestBody ScanFolderDTO dto) {
        return ResponseEntity.ok(folderService.addFolder(dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScanFolderDTO> getFolder(@PathVariable Long id) {
        return ResponseEntity.ok(folderService.getFolder(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFolder(@PathVariable Long id) {
        folderService.deleteFolder(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/scan")
    public ResponseEntity<Map<String, String>> scanFolder(@PathVariable Long id) {
        folderService.scanFolder(id);
        return ResponseEntity.ok(Map.of("message", "扫描已开始"));
    }

    @GetMapping("/{id}/photos")
    public ResponseEntity<Page<PhotoDTO>> getFolderPhotos(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(folderService.getFolderPhotos(id, PageRequest.of(page, size)));
    }

    /**
     * Browse directories at a given path.
     * If path is empty, returns root directories.
     */
    @GetMapping("/browse")
    public ResponseEntity<List<Map<String, Object>>> browseFolders(
            @RequestParam(defaultValue = "") String path) {
        return ResponseEntity.ok(folderService.browseDirectories(path));
    }
}
