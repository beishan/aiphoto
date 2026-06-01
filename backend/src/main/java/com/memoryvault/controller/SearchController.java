package com.memoryvault.controller;

import com.memoryvault.dto.PhotoDTO;
import com.memoryvault.dto.SearchRequest;
import com.memoryvault.entity.User;
import com.memoryvault.repository.UserRepository;
import com.memoryvault.service.SearchService;
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
