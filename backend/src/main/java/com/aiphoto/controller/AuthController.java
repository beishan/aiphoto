package com.aiphoto.controller;

import com.aiphoto.dto.LoginRequest;
import com.aiphoto.dto.LoginResponse;
import com.aiphoto.dto.UserDTO;
import com.aiphoto.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }

    @PostMapping("/register")
    public ResponseEntity<UserDTO> register(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.register(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserDTO> getCurrentUser() {
        // TODO: get from SecurityContext
        return ResponseEntity.ok().build();
    }
}
