package com.retrorental.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.retrorental.backend.config.SecurityConfig;
import com.retrorental.backend.dto.response.TicketAnalysisResponse;
import com.retrorental.backend.dto.response.TicketResponse;
import com.retrorental.backend.exception.ErrorCode;
import com.retrorental.backend.exception.ResourceNotFoundException;
import com.retrorental.backend.security.JwtFilter;
import com.retrorental.backend.service.TicketService;
import java.time.LocalDateTime;
import com.retrorental.backend.model.enums.UnidadUso;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Tests de /tickets (creacion multipart, analisis OCR y lectura). Requieren
 * estar autenticado. La creacion valida @ModelAttribute + partes multipart.
 */
@WebMvcTest(controllers = TicketController.class)
@Import({SecurityConfig.class, JwtFilter.class})
class TicketControllerTest extends AbstractControllerTest {

    @MockitoBean
    private TicketService ticketService;

    private TicketResponse sampleTicket() {
        return new TicketResponse(1, 42.5, LocalDateTime.now(), 1, 1, 1,
            "juanperez", 1250, UnidadUso.HORAS, new BigDecimal("2086.00"),
            "tickets/k1.jpg", "http://url/1",
            "tableros/k2.jpg", "http://url/2");
    }

    @Test
    @WithMockUser(username = "juanperez", roles = "EMPLEADO")
    void crear_conDatosValidos_devuelve200() throws Exception {
        when(ticketService.create(any(), anyString())).thenReturn(sampleTicket());

        var ticketFoto = new MockMultipartFile(
            "ticketFoto", "ticket.jpg", "image/jpeg", "fake".getBytes());
        var tableroFoto = new MockMultipartFile(
            "tableroFoto", "tablero.jpg", "image/jpeg", "fake".getBytes());

        mockMvc.perform(multipart("/tickets")
                .file(ticketFoto)
                .file(tableroFoto)
                .param("litros", "42.5")
                .param("idPrecio", "1")
                .param("idProveedor", "1")
                .param("idVehiculo", "1")
                .param("usoAcumulado", "1250"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.litros").value(42.5))
            .andExpect(jsonPath("$.usoAcumulado").value(1250))
            .andExpect(jsonPath("$.unidadUso").value("HORAS"));
    }

    @Test
    @WithMockUser(username = "juanperez", roles = "EMPLEADO")
    void crear_sinFoto_devuelve200() throws Exception {
        // La foto del ticket es OPCIONAL: se puede registrar una carga sin
        // comprobante (empleados en campo / equipos de gama baja).
        when(ticketService.create(any(), anyString())).thenReturn(sampleTicket());

        mockMvc.perform(multipart("/tickets")
                .param("litros", "42.5")
                .param("idPrecio", "1")
                .param("idProveedor", "1")
                .param("idVehiculo", "1")
                .param("usoAcumulado", "1250"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(username = "juanperez", roles = "EMPLEADO")
    void crear_sinLecturaDelContador_devuelve400() throws Exception {
        // usoAcumulado es obligatorio: sin el no se puede calcular consumo.
        mockMvc.perform(multipart("/tickets")
                .param("litros", "42.5")
                .param("idPrecio", "1")
                .param("idProveedor", "1")
                .param("idVehiculo", "1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void crear_sinLitros_devuelve400() throws Exception {
        var ticketFoto = new MockMultipartFile(
            "ticketFoto", "ticket.jpg", "image/jpeg", "fake".getBytes());
        var tableroFoto = new MockMultipartFile(
            "tableroFoto", "tablero.jpg", "image/jpeg", "fake".getBytes());

        mockMvc.perform(multipart("/tickets")
                .file(ticketFoto)
                .file(tableroFoto)
                .param("idPrecio", "1")
                .param("idProveedor", "1")
                .param("idVehiculo", "1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void crear_sinToken_devuelve401() throws Exception {
        var ticketFoto = new MockMultipartFile(
            "ticketFoto", "ticket.jpg", "image/jpeg", "fake".getBytes());

        mockMvc.perform(multipart("/tickets").file(ticketFoto))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void analyze_conFoto_devuelve200() throws Exception {
        when(ticketService.analyze(any())).thenReturn(new TicketAnalysisResponse(
            50.0, LocalDateTime.now(), 100000.0, 2000.0, "YPF",
            com.retrorental.backend.model.enums.TipoCombustible.NAFTA_SUPER, 3, "YPF Centro",
            5, new java.math.BigDecimal("2000")));

        var ticketFoto = new MockMultipartFile(
            "ticketFoto", "ticket.jpg", "image/jpeg", "fake".getBytes());

        mockMvc.perform(multipart("/tickets/analyze").file(ticketFoto))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estacion").value("YPF"));
    }

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void get_ticketExistente_devuelve200() throws Exception {
        when(ticketService.get(1)).thenReturn(sampleTicket());

        mockMvc.perform(get("/tickets/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.empleadoUsername").value("juanperez"));
    }

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void get_ticketInexistente_devuelve404() throws Exception {
        when(ticketService.get(eq(99))).thenThrow(new ResourceNotFoundException(
            ErrorCode.TICKET_NOT_FOUND, "Ticket no encontrado"));

        mockMvc.perform(get("/tickets/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void get_idNoNumerico_devuelve400() throws Exception {
        mockMvc.perform(get("/tickets/abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
            .andExpect(jsonPath("$.field").value("id"));
    }
}
