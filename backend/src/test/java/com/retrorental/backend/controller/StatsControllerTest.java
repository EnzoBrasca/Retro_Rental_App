package com.retrorental.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.retrorental.backend.config.SecurityConfig;
import com.retrorental.backend.dto.response.StatsResponse;
import com.retrorental.backend.security.JwtFilter;
import com.retrorental.backend.security.SecurityEventLogger;
import com.retrorental.backend.service.StatsService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Tests de /admin/stats (daily/weekly/monthly). Reservado a ADMINISTRADOR.
 */
@WebMvcTest(controllers = StatsController.class)
@Import({SecurityConfig.class, JwtFilter.class, SecurityEventLogger.class})
@Tag("stats")
class StatsControllerTest extends AbstractControllerTest {

    @MockitoBean
    private StatsService statsService;

    private StatsResponse sample() {
        return new StatsResponse(
            LocalDate.of(2026, 7, 8), LocalDate.of(2026, 7, 8),
            new BigDecimal("120.5"), new BigDecimal("240000"), 4L, 3, new BigDecimal("40.16"),
            // Sin vehiculo filtrado no hay consumo: es el caso por defecto del panel.
            null, null,
            List.of(), List.of());
    }

    @Test
    void daily_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/admin/stats/daily"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void daily_comoEmpleado_devuelve403() throws Exception {
        mockMvc.perform(get("/admin/stats/daily"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void daily_comoAdmin_devuelve200() throws Exception {
        when(statsService.daily(any(), any(), any())).thenReturn(sample());

        mockMvc.perform(get("/admin/stats/daily"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalLitros").value(120.5));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void weekly_comoAdmin_devuelve200() throws Exception {
        when(statsService.weekly(any(), any(), any())).thenReturn(sample());

        mockMvc.perform(get("/admin/stats/weekly").param("fecha", "2026-07-08"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cantidadRegistros").value(4));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void monthly_comoAdmin_devuelve200() throws Exception {
        when(statsService.monthly(any(), any(), any())).thenReturn(sample());

        mockMvc.perform(get("/admin/stats/monthly"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.vehiculosActivos").value(3));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void daily_conFechaInvalida_devuelve400() throws Exception {
        mockMvc.perform(get("/admin/stats/daily").param("fecha", "no-es-fecha"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
            .andExpect(jsonPath("$.field").value("fecha"));
    }
}
