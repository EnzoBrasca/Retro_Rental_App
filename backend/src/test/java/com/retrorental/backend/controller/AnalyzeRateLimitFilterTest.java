package com.retrorental.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.retrorental.backend.config.SecurityConfig;
import com.retrorental.backend.dto.response.TicketAnalysisResponse;
import com.retrorental.backend.security.AnalyzeRateLimitFilter;
import com.retrorental.backend.security.JwtFilter;
import com.retrorental.backend.security.LoginRateLimitFilter;
import com.retrorental.backend.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * Tests del freno de consumo sobre POST /tickets/analyze.
 *
 * Este endpoint es el unico de la API que cuesta dinero por llamada (cada
 * request es un OCR pago de Mistral) y el que puede dejar sin hilos al resto de
 * la app. El limite se baja a 3 para no disparar decenas de requests; la
 * ventana se deja larga a proposito, para que no venza durante el test y el
 * resultado no dependa del reloj.
 *
 * CADA TEST USA SU PROPIO USUARIO. El filtro es un singleton del contexto de
 * Spring, que se comparte entre los metodos de la clase: si dos tests usaran la
 * misma cuenta, el primero le dejaria el contador gastado al segundo y el
 * resultado dependeria del orden de ejecucion. Es el mismo motivo por el que
 * LoginRateLimitFilterTest usa una IP distinta por test.
 */
@WebMvcTest(controllers = TicketController.class)
@Import({SecurityConfig.class, JwtFilter.class, LoginRateLimitFilter.class,
         AnalyzeRateLimitFilter.class})
@TestPropertySource(properties = {
    "app.rate-limit.analyze.max-attempts=3",
    "app.rate-limit.analyze.window-seconds=600"
})
class AnalyzeRateLimitFilterTest extends AbstractControllerTest {

    @MockitoBean
    private TicketService ticketService;

    private MockMultipartHttpServletRequestBuilder analisis() {
        return multipart("/tickets/analyze")
            .file(new MockMultipartFile("ticketFoto", "t.jpg", "image/jpeg", new byte[]{1, 2, 3}));
    }

    private TicketAnalysisResponse respuestaVacia() {
        return new TicketAnalysisResponse(
            null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void devuelve429_alSuperarElLimitePorUsuario() throws Exception {
        when(ticketService.analyze(any())).thenReturn(respuestaVacia());

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(analisis().with(usuario("limite-basico")))
                .andExpect(status().isOk());
        }

        mockMvc.perform(analisis().with(usuario("limite-basico")))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "600"))
            .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    /** Pasado el limite, el request no llega al servicio: no se gasta el OCR. */
    @Test
    void alCortar_noLlamaAlServicioDeAnalisis() throws Exception {
        when(ticketService.analyze(any())).thenReturn(respuestaVacia());

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(analisis().with(usuario("no-gasta-ocr")));
        }

        // 3 pasaron, el 4to lo corto el filtro.
        verify(ticketService, org.mockito.Mockito.times(3)).analyze(any());
    }

    /**
     * El corazon de por que el limite es por usuario y no por IP: los empleados
     * cargan desde el campo compartiendo la salida de la operadora movil. Que
     * uno agote su cuota no puede dejar sin OCR a sus companeros.
     */
    @Test
    void unUsuarioPasadoDeLimite_noAfectaAOtro() throws Exception {
        when(ticketService.analyze(any())).thenReturn(respuestaVacia());

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(analisis().with(usuario("companero-pasado")));
        }
        mockMvc.perform(analisis().with(usuario("companero-pasado")))
            .andExpect(status().isTooManyRequests());

        // Otra cuenta, misma "IP": tiene su propio contador intacto.
        mockMvc.perform(analisis().with(usuario("companero-sano")))
            .andExpect(status().isOk());
    }

    /** El limite no toca los demas endpoints de tickets. */
    @Test
    void noLimitaLosDemasEndpointsDeTickets() throws Exception {
        when(ticketService.analyze(any())).thenReturn(respuestaVacia());

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(analisis().with(usuario("solo-analyze")));
        }
        mockMvc.perform(analisis().with(usuario("solo-analyze")))
            .andExpect(status().isTooManyRequests());

        // /tickets/analyze ya esta cortado para esa cuenta, pero el historial
        // del MISMO usuario sigue respondiendo: el freno es de ese endpoint, no
        // de la sesion.
        when(ticketService.listMine(any())).thenReturn(java.util.List.of());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/tickets/me").with(usuario("solo-analyze")))
            .andExpect(status().isOk());
    }

    /** Sin autenticar no hay a quien contarle: corta la cadena de seguridad, no el filtro. */
    @Test
    void sinAutenticar_devuelve401YNoLlamaAlServicio() throws Exception {
        mockMvc.perform(analisis()).andExpect(status().isUnauthorized());

        verify(ticketService, never()).analyze(any());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor usuario(
            String username) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
            .user(username).roles("EMPLEADO");
    }
}
