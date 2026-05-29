package com.memoryvault.controller;

import com.memoryvault.entity.User;
import com.memoryvault.repository.UserRepository;
import com.memoryvault.service.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingController {

    private final SettingService settingService;
    private final UserRepository userRepository;

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

    private Long getUserId(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getId();
    }
}
