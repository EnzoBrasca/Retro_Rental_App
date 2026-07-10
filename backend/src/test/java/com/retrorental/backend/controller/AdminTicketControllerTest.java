package com.retrorental.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.retrorental.backend.config.SecurityConfig;
import com.retrorental.backend.dto.response.TicketResponse;
import com.retrorental.backend.security.JwtFilter;
import com.retrorental.backend.service.TicketService;
import java.time.LocalDateTime;
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
            "emp@example.com", "tickets/k1.jpg", "http://url/1",
            "tableros/k2.jpg", "http://url/2");
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
        when(ticketService.listForAdmin(any(), any(), any(), any(), any(), any()))
            .thenReturn(samplePage());

        mockMvc.perform(get("/admin/tickets"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void list_conFiltros_devuelve200() throws Exception {
        when(ticketService.listForAdmin(any(), any(), any(), any(), any(), any()))
            .thenReturn(samplePage());

        mockMvc.perform(get("/admin/tickets")
                .param("proveedorId", "3")
                .param("desde", "2026-07-01T00:00:00")
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].empleadoEmail").value("emp@example.com"));
    }
}
