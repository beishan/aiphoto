package com.memoryvault.controller;

import com.memoryvault.dto.UserDTO;
import com.memoryvault.dto.UserProfileUpdateRequest;
import com.memoryvault.entity.User;
import com.memoryvault.repository.UserRepository;
import com.memoryvault.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserDTO>> listUsers() {
        return ResponseEntity.ok(userService.listUsers());
    }

    @GetMapping("/me")
    public ResponseEntity<UserDTO> getCurrentUser(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(userService.getUserById(user.getId()));
    }

    @PutMapping("/me")
    public ResponseEntity<UserDTO> updateCurrentUser(
            Authentication authentication,
            @Valid @RequestBody UserProfileUpdateRequest request) {
        return ResponseEntity.ok(
                userService.updateCurrentProfile(authentication.getName(), request));
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserDTO> uploadCurrentUserAvatar(
            Authentication authentication,
            @RequestParam("file") MultipartFile file) throws Exception {
        return ResponseEntity.ok(userService.uploadAvatar(authentication.getName(), file));
    }

    @DeleteMapping("/me/avatar")
    public ResponseEntity<UserDTO> deleteCurrentUserAvatar(Authentication authentication) {
        return ResponseEntity.ok(userService.deleteAvatar(authentication.getName()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDTO> createUser(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        String role = request.getOrDefault("role", "USER");
        String nickname = request.get("nickname");
        return ResponseEntity.ok(userService.createUser(username, password, role, nickname));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> request) {
        userService.resetPassword(id, request.get("password"));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/toggle-enabled")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDTO> toggleEnabled(@PathVariable Long id) {
        return ResponseEntity.ok(userService.toggleEnabled(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDTO> updateUser(@PathVariable Long id, @RequestBody Map<String, String> request) {
        String nickname = request.get("nickname");
        String role = request.get("role");
        return ResponseEntity.ok(userService.updateUser(id, nickname, role));
    }
}
