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
import com.retrorental.backend.dto.request.CreateVehiculoRequest;
import com.retrorental.backend.dto.response.VehiculoResponse;
import com.retrorental.backend.exception.ConflictException;
import com.retrorental.backend.exception.ErrorCode;
import com.retrorental.backend.exception.ResourceNotFoundException;
import com.retrorental.backend.model.enums.Estado;
import com.retrorental.backend.model.enums.TipoCombustible;
import com.retrorental.backend.model.enums.TipoVehiculo;
import com.retrorental.backend.security.JwtFilter;
import com.retrorental.backend.service.VehiculoService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Tests de /admin/vehiculos (ABM). Reservado a ADMINISTRADOR: se ejercen tanto
 * el camino feliz por rol como los rechazos 401 (sin token) y 403 (rol
 * incorrecto), ademas de validaciones y errores de negocio.
 */
@WebMvcTest(controllers = AdminVehiculoController.class)
@Import({SecurityConfig.class, JwtFilter.class})
class AdminVehiculoControllerTest extends AbstractControllerTest {

    @MockitoBean
    private VehiculoService vehiculoService;

    private CreateVehiculoRequest validCreate() {
        CreateVehiculoRequest req = new CreateVehiculoRequest();
        req.setPatente("ABC123");
        req.setTipoVehiculo(TipoVehiculo.CAMIONETA);
        req.setTipoCombustible(TipoCombustible.GASOIL_GRADO_2);
        req.setCapacidadTanque(60);
        req.setEstado(Estado.DISPONIBLE);
        req.setFechaUltimoMantenimiento(LocalDate.now().minusMonths(1));
        req.setKilometraje(15000);
        req.setConsumoPromedio(new BigDecimal("8.5"));
        return req;
    }

    private VehiculoResponse sampleResponse() {
        return new VehiculoResponse(1, "ABC123", TipoVehiculo.CAMIONETA,
            TipoCombustible.GASOIL_GRADO_2, Estado.DISPONIBLE, 60, 15000, new BigDecimal("8.5"), null, null, null, null);
    }

    @Test
    void listar_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/admin/vehiculos"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void listar_comoEmpleado_devuelve403() throws Exception {
        mockMvc.perform(get("/admin/vehiculos"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void listar_comoAdmin_devuelve200() throws Exception {
        when(vehiculoService.listAll()).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/admin/vehiculos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].patente").value("ABC123"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void crear_conDatosValidos_devuelve200() throws Exception {
        when(vehiculoService.create(any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/admin/vehiculos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCreate())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void crear_conPatenteInvalida_devuelve400() throws Exception {
        CreateVehiculoRequest req = validCreate();
        req.setPatente("!!");

        mockMvc.perform(post("/admin/vehiculos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors[0].field").value("patente"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void crear_conPatenteDuplicada_devuelve409() throws Exception {
        when(vehiculoService.create(any())).thenThrow(new ConflictException(
            ErrorCode.PATENTE_ALREADY_EXISTS, "Ya existe un vehiculo con esa patente", "patente"));

        mockMvc.perform(post("/admin/vehiculos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCreate())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PATENTE_ALREADY_EXISTS"))
            .andExpect(jsonPath("$.field").value("patente"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void editar_vehiculoInexistente_devuelve404() throws Exception {
        when(vehiculoService.update(eq(99), any())).thenThrow(new ResourceNotFoundException(
            ErrorCode.VEHICULO_NOT_FOUND, "Vehiculo no encontrado"));

        mockMvc.perform(put("/admin/vehiculos/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCreate())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("VEHICULO_NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void desactivar_devuelve204() throws Exception {
        doNothing().when(vehiculoService).desactivar(1);

        mockMvc.perform(delete("/admin/vehiculos/1"))
            .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void desactivar_yaDadoDeBaja_devuelve409() throws Exception {
        doThrow(new ConflictException(
            ErrorCode.VEHICULO_ALREADY_INACTIVE, "El vehiculo ya está dado de baja"))
            .when(vehiculoService).desactivar(1);

        mockMvc.perform(delete("/admin/vehiculos/1"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("VEHICULO_ALREADY_INACTIVE"));
    }
}
