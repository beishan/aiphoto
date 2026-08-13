package com.memoryvault.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class UserDTO {
    private Long id;
    private String username;
    private String role;
    private String avatar;
    private String nickname;
    private String mood;
    private LocalDate birthDate;
    private String photoPreferences;
    private String notes;
    private String theme;
    private DockConfigDTO dockConfig;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
}
