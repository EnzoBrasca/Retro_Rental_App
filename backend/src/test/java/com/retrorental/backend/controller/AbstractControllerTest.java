package com.retrorental.backend.controller;

import com.retrorental.backend.repository.PersonaRepository;
import com.retrorental.backend.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * Base para los tests de la capa web (@WebMvcTest).
 *
 * Cada test concreto importa SecurityConfig + JwtFilter para ejercer la
 * seguridad REAL (reglas por rol, respuestas 401/403 como ApiError). El
 * JwtFilter necesita estos dos colaboradores; se mockean porque los tests
 * autentican con @WithMockUser, no con tokens reales.
 */
abstract class AbstractControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    protected JwtUtil jwtUtil;

    @MockitoBean
    protected PersonaRepository personaRepository;
}
