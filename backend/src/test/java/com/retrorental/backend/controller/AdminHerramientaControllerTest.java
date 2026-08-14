package com.retrorental.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.retrorental.backend.config.SecurityConfig;
import com.retrorental.backend.dto.request.CreateHerramientaRequest;
import com.retrorental.backend.dto.response.HerramientaResponse;
import com.retrorental.backend.exception.ConflictException;
import com.retrorental.backend.exception.ErrorCode;
import com.retrorental.backend.exception.ResourceNotFoundException;
import com.retrorental.backend.security.JwtFilter;
import com.retrorental.backend.service.HerramientaService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Tests de /admin/herramientas (ABM). Reservado a ADMINISTRADOR: mismo patron
 * que AdminVehiculoControllerTest (401 sin token, 403 con rol incorrecto).
 */
@WebMvcTest(controllers = AdminHerramientaController.class)
@Import({SecurityConfig.class, JwtFilter.class})
@Tag("herramienta")
class AdminHerramientaControllerTest extends AbstractControllerTest {

    @MockitoBean
    private HerramientaService herramientaService;

    private CreateHerramientaRequest validCreate() {
        CreateHerramientaRequest req = new CreateHerramientaRequest();
        req.setNombre("Motosierra Stihl");
        req.setCapacidad(new BigDecimal("0.30"));
        return req;
    }

    private HerramientaResponse sampleResponse() {
        return new HerramientaResponse(1, "Motosierra Stihl", new BigDecimal("0.30"), null);
    }

    @Test
    void listar_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/admin/herramientas"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void listar_comoEmpleado_devuelve403() throws Exception {
        mockMvc.perform(get("/admin/herramientas"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void listar_comoAdmin_devuelve200() throws Exception {
        when(herramientaService.listAll()).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/admin/herramientas"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].nombre").value("Motosierra Stihl"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void crear_conDatosValidos_devuelve200() throws Exception {
        when(herramientaService.create(any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/admin/herramientas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCreate())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void crear_sinNombre_devuelve400() throws Exception {
        CreateHerramientaRequest req = validCreate();
        req.setNombre(null);

        mockMvc.perform(post("/admin/herramientas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors[0].field").value("nombre"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void crear_sinCapacidad_devuelve400() throws Exception {
        CreateHerramientaRequest req = validCreate();
        req.setCapacidad(null);

        mockMvc.perform(post("/admin/herramientas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors[0].field").value("capacidad"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void editar_herramientaInexistente_devuelve404() throws Exception {
        when(herramientaService.update(eq(99), any())).thenThrow(new ResourceNotFoundException(
            ErrorCode.HERRAMIENTA_NOT_FOUND, "Herramienta no encontrada"));

        mockMvc.perform(put("/admin/herramientas/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCreate())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("HERRAMIENTA_NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void desactivar_devuelve204() throws Exception {
        doNothing().when(herramientaService).desactivar(1);

        mockMvc.perform(delete("/admin/herramientas/1"))
            .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void desactivar_yaDadaDeBaja_devuelve409() throws Exception {
        doThrow(new ConflictException(
            ErrorCode.HERRAMIENTA_ALREADY_INACTIVE, "La herramienta ya está dada de baja"))
            .when(herramientaService).desactivar(1);

        mockMvc.perform(delete("/admin/herramientas/1"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("HERRAMIENTA_ALREADY_INACTIVE"));
    }
}
