package com.memoryvault.controller;

import com.memoryvault.entity.User;
import com.memoryvault.repository.UserRepository;
import com.memoryvault.service.SettingService;
import com.memoryvault.service.ModelManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingController {

    private final SettingService settingService;
    private final UserRepository userRepository;
    private final ModelManagementService modelManagementService;

    @GetMapping
    public ResponseEntity<Map<String, String>> getSettings(Authentication authentication) {
        Long userId = getUserId(authentication);
        return ResponseEntity.ok(settingService.getAllSettings(userId));
    }

    @PutMapping
    public ResponseEntity<Void> updateSettings(Authentication authentication, @RequestBody Map<String, String> settings) {
        Long userId = getUserId(authentication);
        settingService.updateSettings(userId, settings);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> getModels() {
        return ResponseEntity.ok(modelManagementService.getStatus());
    }

    @GetMapping("/models/files")
    public ResponseEntity<List<ModelManagementService.ModelFile>> browseModels(
            @RequestParam(defaultValue = "") String directory) throws IOException {
        return ResponseEntity.ok(modelManagementService.browse(directory));
    }

    @PutMapping("/models/{modelName}")
    public ResponseEntity<Map<String, Object>> configureModel(
            @PathVariable String modelName,
            @RequestBody ModelConfigRequest request) {
        return ResponseEntity.ok(modelManagementService.configure(
                modelName, request.path(), request.enabled()
        ));
    }

    @PostMapping("/models/{modelName}/reload")
    public ResponseEntity<Map<String, Object>> reloadModel(@PathVariable String modelName) {
        return ResponseEntity.ok(modelManagementService.reload(modelName));
    }

    @PostMapping(value = "/models/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ModelManagementService.ModelFile> uploadModel(
            @RequestParam(defaultValue = "") String directory,
            @RequestPart("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(modelManagementService.upload(directory, file));
    }

    public record ModelConfigRequest(String path, boolean enabled) {
    }

    private Long getUserId(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getId();
    }
}
