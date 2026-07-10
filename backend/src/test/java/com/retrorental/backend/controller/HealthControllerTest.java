package com.retrorental.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Test del endpoint publico /health. Se testea con standaloneSetup (aislado):
 * es un handler trivial sin seguridad ni dependencias.
 */
class HealthControllerTest {

    private final MockMvc mockMvc =
        MockMvcBuilders.standaloneSetup(new HealthController()).build();

    @Test
    void health_devuelve200_conStatusOk() throws Exception {
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"))
            .andExpect(jsonPath("$.service").exists());
    }
}
