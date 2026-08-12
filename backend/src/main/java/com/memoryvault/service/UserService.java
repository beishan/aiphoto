package com.memoryvault.service;

import com.memoryvault.dto.LoginRequest;
import com.memoryvault.dto.LoginResponse;
import com.memoryvault.dto.UserDTO;
import com.memoryvault.dto.UserProfileUpdateRequest;
import com.memoryvault.entity.User;
import com.memoryvault.repository.UserRepository;
import com.memoryvault.security.JwtService;
import com.memoryvault.storage.LocalStorageService;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final LocalStorageService storageService;

    private static final long MAX_AVATAR_SIZE = 5L * 1024 * 1024;
    private static final Set<String> AVATAR_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Lazy AuthenticationManager authenticationManager,
            LocalStorageService storageService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.storageService = storageService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        if (!user.getEnabled()) {
            throw new RuntimeException("账号已被禁用");
        }
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())
                .build();
    }

    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        User user = userRepository.findByUsername(request.getUsername()).orElseThrow();
        if (!user.getEnabled()) {
            throw new RuntimeException("账号已被禁用");
        }
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        UserDetails userDetails = loadUserByUsername(request.getUsername());
        String token = jwtService.generateToken(userDetails);
        return new LoginResponse(token, toDTO(user));
    }

    public UserDTO register(LoginRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(User.UserRole.USER);
        user.setEnabled(true);
        user = userRepository.save(user);
        return toDTO(user);
    }

    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return toDTO(user);
    }

    @Transactional
    public UserDTO updateCurrentProfile(String username, UserProfileUpdateRequest request) {
        User user = findByUsername(username);
        user.setNickname(normalize(request.getNickname()));
        user.setMood(normalize(request.getMood()));
        user.setBirthDate(request.getBirthDate());
        user.setPhotoPreferences(normalize(request.getPhotoPreferences()));
        user.setProfileNotes(normalize(request.getNotes()));
        return toDTO(userRepository.save(user));
    }

    @Transactional
    public UserDTO uploadAvatar(String username, MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择头像图片");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new IllegalArgumentException("头像图片不能超过5MB");
        }

        byte[] data = file.getBytes();
        String contentType = detectAvatarContentType(data);
        if (contentType == null || !AVATAR_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("仅支持 JPG、PNG、WebP 或 GIF 图片");
        }

        User user = findByUsername(username);
        String extension = switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };
        String objectName = "avatars/" + user.getId() + "/" + UUID.randomUUID() + extension;
        storageService.uploadPhoto(data, objectName, contentType);

        String oldAvatar = user.getAvatar();
        user.setAvatar(storageService.getPhotoUrl(objectName));
        User saved = userRepository.save(user);
        deleteManagedAvatar(user.getId(), oldAvatar);
        return toDTO(saved);
    }

    @Transactional
    public UserDTO deleteAvatar(String username) {
        User user = findByUsername(username);
        String oldAvatar = user.getAvatar();
        user.setAvatar(null);
        User saved = userRepository.save(user);
        deleteManagedAvatar(user.getId(), oldAvatar);
        return toDTO(saved);
    }

    @Transactional
    public UserDTO updateCurrentTheme(String username, String theme) {
        if (!Set.of("dark", "light", "macos26").contains(theme)) {
            throw new IllegalArgumentException("不支持的主题风格");
        }
        User user = findByUsername(username);
        user.setTheme(theme);
        return toDTO(userRepository.save(user));
    }

    private User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    private String normalize(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String detectAvatarContentType(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 'P'
                && bytes[2] == 'N'
                && bytes[3] == 'G') {
            return "image/png";
        }
        if (bytes.length >= 6
                && bytes[0] == 'G'
                && bytes[1] == 'I'
                && bytes[2] == 'F') {
            return "image/gif";
        }
        if (bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private void deleteManagedAvatar(Long userId, String avatarUrl) {
        String prefix = "/media/photos/avatars/" + userId + "/";
        if (avatarUrl == null || !avatarUrl.startsWith(prefix)) {
            return;
        }
        try {
            storageService.deleteObject(avatarUrl.substring("/media/photos/".length()));
        } catch (Exception ignored) {
            // The profile update should remain successful if stale avatar cleanup fails.
        }
    }

    // ===== Admin: User Management =====

    public List<UserDTO> listUsers() {
        return userRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public UserDTO createUser(String username, String password, String role, String nickname) {
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("用户名已存在");
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(User.UserRole.valueOf(role.toUpperCase()));
        user.setNickname(nickname);
        user.setEnabled(true);
        user = userRepository.save(user);
        return toDTO(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // Prevent deleting the last admin
        if (user.getRole() == User.UserRole.ADMIN && user.getEnabled()) {
            long adminCount = userRepository.countAdminUsers();
            if (adminCount <= 1) {
                throw new RuntimeException("不允许删除最后一个管理员账号");
            }
        }

        userRepository.deleteById(id);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public UserDTO toggleEnabled(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // Prevent disabling the last admin
        if (user.getRole() == User.UserRole.ADMIN && user.getEnabled()) {
            long adminCount = userRepository.countAdminUsers();
            if (adminCount <= 1) {
                throw new RuntimeException("不允许禁用最后一个管理员账号");
            }
        }

        user.setEnabled(!user.getEnabled());
        user = userRepository.save(user);
        return toDTO(user);
    }

    @Transactional
    public UserDTO updateUser(Long id, String nickname, String role) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (nickname != null) {
            user.setNickname(nickname);
        }
        if (role != null) {
            // Prevent demoting the last admin
            if (user.getRole() == User.UserRole.ADMIN && !"ADMIN".equals(role.toUpperCase())) {
                long adminCount = userRepository.countAdminUsers();
                if (adminCount <= 1) {
                    throw new RuntimeException("不允许降级最后一个管理员账号");
                }
            }
            user.setRole(User.UserRole.valueOf(role.toUpperCase()));
        }

        user = userRepository.save(user);
        return toDTO(user);
    }

    private UserDTO toDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setRole(user.getRole().name());
        dto.setAvatar(user.getAvatar());
        dto.setNickname(user.getNickname());
        dto.setMood(user.getMood());
        dto.setBirthDate(user.getBirthDate());
        dto.setPhotoPreferences(user.getPhotoPreferences());
        dto.setNotes(user.getProfileNotes());
        dto.setTheme(user.getTheme());
        dto.setEnabled(user.getEnabled());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setLastLoginAt(user.getLastLoginAt());
        return dto;
    }
}
