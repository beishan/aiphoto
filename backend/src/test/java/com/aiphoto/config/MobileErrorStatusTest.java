package com.aiphoto.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aiphoto.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

class MobileErrorStatusTest {
    @RestController
    static class Errors {
        @GetMapping("/missing") String missing() { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
        @GetMapping("/denied") String denied() { throw new org.springframework.security.access.AccessDeniedException("private"); }
    }
    @Test void advicePreservesNotFoundAndForbidden() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new Errors()).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/missing")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("HTTP_404"));
        mvc.perform(get("/denied")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
