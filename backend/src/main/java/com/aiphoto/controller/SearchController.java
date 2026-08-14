package com.aiphoto.controller;

import com.aiphoto.dto.PhotoDTO;
import com.aiphoto.dto.SearchRequest;
import com.aiphoto.entity.User;
import com.aiphoto.repository.UserRepository;
import com.aiphoto.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<Page<PhotoDTO>> search(@ModelAttribute SearchRequest request, Authentication authentication) {
        Long userId = getUserId(authentication);
        return ResponseEntity.ok(searchService.search(request, userId));
    }

    private Long getUserId(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getId();
    }
}
