package com.retrorental.backend.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.retrorental.backend.config.SecurityConfig;
import com.retrorental.backend.dto.response.VehiculoResponse;
import com.retrorental.backend.model.enums.Estado;
import com.retrorental.backend.model.enums.TipoCombustible;
import com.retrorental.backend.model.enums.TipoVehiculo;
import com.retrorental.backend.model.enums.UnidadUso;
import com.retrorental.backend.security.JwtFilter;
import com.retrorental.backend.service.VehiculoService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Tests de lectura de vehiculos (/vehiculos y /me/vehiculos). Requieren estar
 * autenticado (cualquier rol), sin restriccion adicional.
 */
@WebMvcTest(controllers = VehiculoController.class)
@Import({SecurityConfig.class, JwtFilter.class})
class VehiculoControllerTest extends AbstractControllerTest {

    @MockitoBean
    private VehiculoService vehiculoService;

    private VehiculoResponse sample() {
        return new VehiculoResponse(1, "ABC123", null, TipoVehiculo.CAMION,
            TipoCombustible.GASOIL_GRADO_2, Estado.DISPONIBLE, 60, 15000, UnidadUso.KM,
            new BigDecimal("8.5"), new BigDecimal("9.1"), null, null, null, null);
    }

    @Test
    void listar_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/vehiculos"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void listar_autenticado_devuelve200() throws Exception {
        when(vehiculoService.listAll()).thenReturn(List.of(sample()));

        mockMvc.perform(get("/vehiculos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].identificador").value("ABC123"));
    }

    @Test
    @WithMockUser(username = "emp@example.com", roles = "EMPLEADO")
    void misVehiculos_autenticado_devuelve200() throws Exception {
        when(vehiculoService.listAsignados(anyString())).thenReturn(List.of(sample()));

        mockMvc.perform(get("/me/vehiculos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void misVehiculos_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/me/vehiculos"))
            .andExpect(status().isUnauthorized());
    }
}
