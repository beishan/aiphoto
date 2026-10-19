package com.aiphoto.config;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiphoto.security.JwtAuthenticationFilter;
import com.aiphoto.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.User;

@WebMvcTest(SecurityConfigTest.ProtectedController.class)
@ContextConfiguration(classes = {SecurityConfig.class, JwtAuthenticationFilter.class, SecurityConfigTest.ProtectedController.class})
class SecurityConfigTest {
    @Autowired private MockMvc mvc;
    @MockBean private JwtService jwtService;
    @MockBean private UserDetailsService userDetailsService;

    @RestController
    static class ProtectedController {
        @GetMapping("/api/session-test")
        String protectedEndpoint() { return "ok"; }

        @GetMapping("/api/session-test/admin")
        @PreAuthorize("hasRole('ADMIN')")
        String adminEndpoint() { return "ok"; }
    }

    @Test
    void missingTokenReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/session-test")).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenReturnsUnauthorized() throws Exception {
        when(jwtService.extractUsername("expired")).thenThrow(new IllegalArgumentException("expired"));
        mvc.perform(get("/api/session-test").header("Authorization", "Bearer expired"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenWorksAndInsufficientRoleRemainsForbidden() throws Exception {
        var user = User.withUsername("member").password("unused").roles("USER").build();
        when(jwtService.extractUsername("valid")).thenReturn("member");
        when(userDetailsService.loadUserByUsername("member")).thenReturn(user);
        when(jwtService.isTokenValid("valid", user)).thenReturn(true);
        mvc.perform(get("/api/session-test").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/session-test/admin").header("Authorization", "Bearer valid"))
                .andExpect(status().isForbidden());
    }
}
