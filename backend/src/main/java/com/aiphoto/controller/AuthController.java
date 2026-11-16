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
    private final com.aiphoto.repository.UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
            jakarta.servlet.http.HttpServletRequest servletRequest,
            jakarta.servlet.http.HttpServletResponse response) {
        LoginResponse login = userService.login(request);
        com.aiphoto.security.MediaSessionCookie.write(response, servletRequest, login.getToken());
        return ResponseEntity.ok(login);
    }

    @PostMapping("/register")
    public ResponseEntity<UserDTO> register(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.register(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(jakarta.servlet.http.HttpServletResponse response) {
        com.aiphoto.security.MediaSessionCookie.clear(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserDTO> getCurrentUser(org.springframework.security.core.Authentication auth) {
        var user = userRepository.findByUsername(auth.getName()).orElseThrow();
        return ResponseEntity.ok(userService.getUserById(user.getId()));
    }
}
