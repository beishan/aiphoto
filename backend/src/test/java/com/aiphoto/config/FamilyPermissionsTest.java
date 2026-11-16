package com.aiphoto.config;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiphoto.controller.PeopleController;
import com.aiphoto.repository.UserRepository;
import com.aiphoto.security.JwtAuthenticationFilter;
import com.aiphoto.security.JwtService;
import com.aiphoto.service.FaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PeopleController.class)
@ContextConfiguration(classes = {SecurityConfig.class, JwtAuthenticationFilter.class, PeopleController.class})
class FamilyPermissionsTest {
  @Autowired MockMvc mvc;
  @MockBean JwtService jwt;
  @MockBean UserDetailsService users;
  @MockBean UserRepository repository;
  @MockBean FaceService faces;

  private void token(String role) {
    var user = User.withUsername(role).password("unused").roles(role).build();
    when(jwt.extractUsername(role)).thenReturn(role);
    when(users.loadUserByUsername(role)).thenReturn(user);
    when(jwt.isTokenValid(role, user)).thenReturn(true);
  }

  @Test void mergeAndReassignRequireAdminOnActualController() throws Exception {
    mvc.perform(post("/api/people/merge").contentType(MediaType.APPLICATION_JSON).content("{\"targetId\":1,\"sourceId\":2}"))
        .andExpect(status().isUnauthorized());
    token("USER");
    mvc.perform(post("/api/people/merge").header("Authorization", "Bearer USER").contentType(MediaType.APPLICATION_JSON).content("{\"targetId\":1,\"sourceId\":2}"))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/people/faces/7/assign").header("Authorization", "Bearer USER").contentType(MediaType.APPLICATION_JSON).content("{\"personId\":1}"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(faces);
    token("ADMIN");
    mvc.perform(post("/api/people/merge").header("Authorization", "Bearer ADMIN").contentType(MediaType.APPLICATION_JSON).content("{\"targetId\":1,\"sourceId\":2}"))
        .andExpect(status().isOk());
    mvc.perform(post("/api/people/faces/7/assign").header("Authorization", "Bearer ADMIN").contentType(MediaType.APPLICATION_JSON).content("{\"personId\":1}"))
        .andExpect(status().isOk());
    verify(faces).mergePeople(1L, 2L); verify(faces).assignFaceToPerson(7L, 1L);
  }
}
