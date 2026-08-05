package com.retrorental.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.retrorental.backend.config.SecurityConfig;
import com.retrorental.backend.dto.response.TicketResponse;
import com.retrorental.backend.exception.ConflictException;
import com.retrorental.backend.exception.ErrorCode;
import com.retrorental.backend.exception.ResourceNotFoundException;
import com.retrorental.backend.security.JwtFilter;
import com.retrorental.backend.service.TicketService;
import java.time.LocalDateTime;
import com.retrorental.backend.model.enums.UnidadUso;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PagedModel;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Tests de /admin/tickets (listado paginado). Reservado a ADMINISTRADOR.
 */
@WebMvcTest(controllers = AdminTicketController.class)
@Import({SecurityConfig.class, JwtFilter.class})
class AdminTicketControllerTest extends AbstractControllerTest {

    @MockitoBean
    private TicketService ticketService;

    private PagedModel<TicketResponse> samplePage() {
        TicketResponse t = new TicketResponse(1, 42.5, LocalDateTime.now(), 1, 1, 1,
            "emp@example.com", 84300, UnidadUso.KM, new BigDecimal("2086.00"),
            "tickets/k1.jpg", "http://url/1",
            "tableros/k2.jpg", "http://url/2", null, null);
        return new PagedModel<>(new PageImpl<>(List.of(t)));
    }

    @Test
    void list_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/admin/tickets"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void list_comoEmpleado_devuelve403() throws Exception {
        mockMvc.perform(get("/admin/tickets"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void list_comoAdmin_devuelve200() throws Exception {
        when(ticketService.listForAdmin(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
            .thenReturn(samplePage());

        mockMvc.perform(get("/admin/tickets"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void list_conFiltros_devuelve200() throws Exception {
        when(ticketService.listForAdmin(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
            .thenReturn(samplePage());

        mockMvc.perform(get("/admin/tickets")
                .param("proveedorId", "3")
                .param("desde", "2026-07-01T00:00:00")
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].empleadoUsername").value("emp@example.com"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void list_conFiltroDeMonto_llegaAlService() throws Exception {
        when(ticketService.listForAdmin(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
            .thenReturn(samplePage());

        mockMvc.perform(get("/admin/tickets")
                .param("montoMin", "10000")
                .param("montoMax", "50000"))
            .andExpect(status().isOk());

        verify(ticketService).listForAdmin(
            any(), any(), any(), any(), any(),
            eq(new BigDecimal("10000")), eq(new BigDecimal("50000")), eq(false), any());
    }

    // -----------------------------------------------------------------------
    // Anulacion (V7). Es un DELETE, pero baja logica: la fila queda.
    // -----------------------------------------------------------------------

    @Test
    void anular_sinToken_devuelve401() throws Exception {
        mockMvc.perform(delete("/admin/tickets/1"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void anular_comoEmpleado_devuelve403() throws Exception {
        // Un operario no puede borrar su propia carga: el ticket es la
        // rendicion de lo que gasto.
        mockMvc.perform(delete("/admin/tickets/1"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMINISTRADOR")
    void anular_comoAdmin_devuelve204() throws Exception {
        doNothing().when(ticketService).anular(anyInt(), anyString());

        mockMvc.perform(delete("/admin/tickets/7"))
            .andExpect(status().isNoContent());

        // El username viaja al service: sin el no se puede registrar QUIEN anulo.
        verify(ticketService).anular(7, "admin");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMINISTRADOR")
    void anular_ticketYaAnulado_devuelve409() throws Exception {
        doThrow(new ConflictException(
            ErrorCode.TICKET_ALREADY_ANULADO, "El ticket ya está anulado"))
            .when(ticketService).anular(anyInt(), anyString());

        mockMvc.perform(delete("/admin/tickets/7"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("TICKET_ALREADY_ANULADO"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMINISTRADOR")
    void anular_ticketInexistente_devuelve404() throws Exception {
        doThrow(new ResourceNotFoundException(
            ErrorCode.TICKET_NOT_FOUND, "Ticket no encontrado"))
            .when(ticketService).anular(anyInt(), anyString());

        mockMvc.perform(delete("/admin/tickets/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));
    }
}
