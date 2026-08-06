package com.memoryvault.service;

import com.memoryvault.dto.LoginRequest;
import com.memoryvault.dto.LoginResponse;
import com.memoryvault.dto.UserDTO;
import com.memoryvault.entity.User;
import com.memoryvault.repository.UserRepository;
import com.memoryvault.security.JwtService;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, @Lazy AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
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
        dto.setEnabled(user.getEnabled());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setLastLoginAt(user.getLastLoginAt());
        return dto;
    }
}
