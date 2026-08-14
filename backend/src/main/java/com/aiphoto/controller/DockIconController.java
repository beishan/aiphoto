package com.aiphoto.controller;

import com.aiphoto.entity.User;
import com.aiphoto.repository.UserRepository;
import com.aiphoto.service.DockIconService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/users/me/dock-icons")
@RequiredArgsConstructor
public class DockIconController {

    private final DockIconService dockIconService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<Map<String, String>> getIcons(Authentication authentication) {
        return ResponseEntity.ok(dockIconService.getIcons(currentUser(authentication)));
    }

    @PostMapping(value = "/{name}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> upload(
            Authentication authentication,
            @PathVariable String name,
            @RequestParam("file") MultipartFile file) throws Exception {
        return ResponseEntity.ok(dockIconService.upload(currentUser(authentication), name, file));
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Map<String, String>> delete(Authentication authentication, @PathVariable String name) {
        return ResponseEntity.ok(dockIconService.delete(currentUser(authentication), name));
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }
}
