package com.retrorental.backend.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.retrorental.backend.config.SecurityConfig;
import com.retrorental.backend.dto.response.HerramientaResponse;
import com.retrorental.backend.security.JwtFilter;
import com.retrorental.backend.service.HerramientaService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Tests de lectura de herramientas (/herramientas). Requiere estar
 * autenticado (cualquier rol), sin restriccion adicional. Solo devuelve las
 * ACTIVAS: la baja se filtra en el service.
 */
@WebMvcTest(controllers = HerramientaController.class)
@Import({SecurityConfig.class, JwtFilter.class})
class HerramientaControllerTest extends AbstractControllerTest {

    @MockitoBean
    private HerramientaService herramientaService;

    private HerramientaResponse sample() {
        return new HerramientaResponse(1, "Motosierra Stihl", new BigDecimal("0.30"), null);
    }

    @Test
    void listar_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/herramientas"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void listar_autenticado_devuelve200() throws Exception {
        when(herramientaService.listActivas()).thenReturn(List.of(sample()));

        mockMvc.perform(get("/herramientas"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].nombre").value("Motosierra Stihl"));
    }
}
