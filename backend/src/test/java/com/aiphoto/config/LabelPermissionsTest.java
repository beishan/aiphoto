package com.aiphoto.config;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.aiphoto.controller.CategoryController;
import com.aiphoto.controller.TagController;
import com.aiphoto.security.JwtAuthenticationFilter;
import com.aiphoto.security.JwtService;
import com.aiphoto.service.CategoryService;
import com.aiphoto.service.TagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({TagController.class, CategoryController.class})
@ContextConfiguration(classes = {SecurityConfig.class, JwtAuthenticationFilter.class, TagController.class, CategoryController.class})
class LabelPermissionsTest {
  @Autowired MockMvc mvc;
  @MockBean JwtService jwt;
  @MockBean UserDetailsService users;
  @MockBean TagService tags;
  @MockBean CategoryService categories;
  private void token(String role) {
    var user = User.withUsername(role).password("unused").roles(role).build();
    when(jwt.extractUsername(role)).thenReturn(role); when(users.loadUserByUsername(role)).thenReturn(user); when(jwt.isTokenValid(role, user)).thenReturn(true);
  }
  @Test void globalDeleteAndMergeRequireAdmin() throws Exception {
    token("USER");
    mvc.perform(delete("/api/tags/1").header("Authorization", "Bearer USER")).andExpect(status().isForbidden());
    mvc.perform(delete("/api/categories/1").header("Authorization", "Bearer USER")).andExpect(status().isForbidden());
    mvc.perform(post("/api/tags/merge").header("Authorization", "Bearer USER").contentType(MediaType.APPLICATION_JSON).content("{\"sourceId\":1,\"targetId\":2}")).andExpect(status().isForbidden());
    verifyNoInteractions(tags, categories);
    token("ADMIN");
    mvc.perform(delete("/api/tags/1").header("Authorization", "Bearer ADMIN")).andExpect(status().isNoContent());
    mvc.perform(delete("/api/categories/1").header("Authorization", "Bearer ADMIN")).andExpect(status().isNoContent());
    verify(tags).deleteTag(1L); verify(categories).deleteCategory(1L);
  }
}
