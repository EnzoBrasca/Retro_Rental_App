package com.retrorental.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.retrorental.backend.config.SecurityConfig;
import com.retrorental.backend.dto.request.CreateHabilitadoRequest;
import com.retrorental.backend.dto.response.HabilitadoResponse;
import com.retrorental.backend.exception.ConflictException;
import com.retrorental.backend.exception.ErrorCode;
import com.retrorental.backend.exception.ResourceNotFoundException;
import com.retrorental.backend.security.JwtFilter;
import com.retrorental.backend.service.HabilitadoService;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Tests de /admin/habilitados (padron de documentos autorizados a registrarse).
 *
 * POR QUE ESTE ARCHIVO IMPORTA MAS QUE UN ABM CUALQUIERA. El padron es la
 * mitigacion de SEC-01 (ver docs/SECURITY-AUDIT.md): /auth/register es publico
 * a proposito, y lo unico que separa "self-service comodo para el empleado" de
 * "cualquiera con la URL se crea una cuenta" es que su documento este en este
 * padron. Este controller es el lado de ESCRITURA de ese control, y hasta ahora
 * no tenia un solo test (ver docs/BACKEND-AUDIT.md, TST-01).
 *
 * Se vigilan tres cosas, en ese orden de importancia:
 *
 * 1. El gate de rol. Si /admin/habilitados quedara accesible a un EMPLEADO,
 *    cualquier empleado podria habilitar documentos y el control se evapora.
 * 2. El tope del alta masiva (MAX_BULK = 500). Esta declarado como @Size sobre
 *    un List<@Valid ...> con @Validated a nivel de clase, que es JUSTO la
 *    combinacion donde Spring falla en silencio si falta una de las piezas: la
 *    validacion no dispara, no hay error, y el endpoint acepta lotes sin techo.
 * 3. Que el formato del documento se valide igual que en el registro. Si aca
 *    entrara un formato que alla no, la habilitacion nunca podria consumirse y
 *    el empleado quedaria trabado sin entender por que.
 */
@WebMvcTest(controllers = AdminHabilitadoController.class)
@Import({SecurityConfig.class, JwtFilter.class})
@Tag("habilitado")
class AdminHabilitadoControllerTest extends AbstractControllerTest {

    @MockitoBean
    private HabilitadoService habilitadoService;

    private CreateHabilitadoRequest validRequest() {
        CreateHabilitadoRequest req = new CreateHabilitadoRequest();
        req.setDocumento("30111222");
        req.setApellido("Gómez");
        req.setNombre("Ana");
        return req;
    }

    private HabilitadoResponse sampleResponse() {
        return new HabilitadoResponse(
            1, "30111222", "Gómez", "Ana", LocalDate.now(), false, null, null);
    }

    // ------------------------------------------------------------------
    // 1. Gate de rol — lo mas importante de este archivo
    // ------------------------------------------------------------------

    @Test
    void listar_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/admin/habilitados"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void listar_comoEmpleado_devuelve403() throws Exception {
        mockMvc.perform(get("/admin/habilitados"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    /**
     * El caso que de verdad importa: un empleado NO puede habilitar documentos.
     * Si esto se rompiera, cualquier cuenta de empleado podria autorizarse a si
     * misma toda la nomina que quisiera.
     */
    @Test
    @WithMockUser(roles = "EMPLEADO")
    void crear_comoEmpleado_devuelve403YNoLlamaAlServicio() throws Exception {
        mockMvc.perform(post("/admin/habilitados")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
            .andExpect(status().isForbidden());

        verify(habilitadoService, never()).create(any());
    }

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void crearMasivo_comoEmpleado_devuelve403YNoLlamaAlServicio() throws Exception {
        mockMvc.perform(post("/admin/habilitados/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(validRequest()))))
            .andExpect(status().isForbidden());

        verify(habilitadoService, never()).createBulk(any());
    }

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void eliminar_comoEmpleado_devuelve403YNoLlamaAlServicio() throws Exception {
        mockMvc.perform(delete("/admin/habilitados/1"))
            .andExpect(status().isForbidden());

        verify(habilitadoService, never()).delete(any());
    }

    // ------------------------------------------------------------------
    // 2. Camino feliz
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void listar_comoAdmin_devuelveElPadron() throws Exception {
        when(habilitadoService.listAll()).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/admin/habilitados"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].documento").value("30111222"))
            .andExpect(jsonPath("$[0].apellido").value("Gómez"))
            .andExpect(jsonPath("$[0].registrado").value(false));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void crear_conDatosValidos_devuelve200() throws Exception {
        when(habilitadoService.create(any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/admin/habilitados")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.documento").value("30111222"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void crear_documentoRepetido_devuelve409() throws Exception {
        when(habilitadoService.create(any())).thenThrow(new ConflictException(
            ErrorCode.HABILITADO_ALREADY_EXISTS, "Ese documento ya está en el padrón", "documento"));

        mockMvc.perform(post("/admin/habilitados")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("HABILITADO_ALREADY_EXISTS"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void eliminar_devuelve204() throws Exception {
        doNothing().when(habilitadoService).delete(1);

        mockMvc.perform(delete("/admin/habilitados/1"))
            .andExpect(status().isNoContent());

        verify(habilitadoService).delete(1);
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void eliminar_habilitacionInexistente_devuelve404() throws Exception {
        doThrow(new ResourceNotFoundException(
            ErrorCode.HABILITADO_NOT_FOUND, "No existe esa habilitación"))
            .when(habilitadoService).delete(eq(99));

        mockMvc.perform(delete("/admin/habilitados/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("HABILITADO_NOT_FOUND"));
    }

    // ------------------------------------------------------------------
    // 3. Validacion del alta masiva — la trampa de Spring
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void crearMasivo_conLoteValido_devuelveElPadron() throws Exception {
        when(habilitadoService.createBulk(any())).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(post("/admin/habilitados/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(validRequest()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].documento").value("30111222"));
    }

    /**
     * EL test del tope. Con 501 documentos el @Size(max = 500) tiene que cortar
     * ANTES de que el servicio vea nada.
     *
     * Si alguien saca el @Validated de la clase, o cambia el List<@Valid ...>
     * por un List<...> a secas, Spring deja de validar la coleccion SIN ningun
     * error visible: el endpoint pasa a aceptar lotes sin techo y nadie se
     * entera hasta que alguien lo usa para llenar la base.
     */
    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void crearMasivo_conMasDe500_rechazaYNoLlamaAlServicio() throws Exception {
        List<CreateHabilitadoRequest> loteEnorme = IntStream.range(0, 501)
            .mapToObj(i -> {
                CreateHabilitadoRequest req = new CreateHabilitadoRequest();
                // Documentos distintos y validos: lo unico que debe fallar es el tamanio.
                req.setDocumento(String.valueOf(30000000 + i));
                req.setApellido("Apellido" + i);
                return req;
            })
            .toList();

        mockMvc.perform(post("/admin/habilitados/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loteEnorme)))
            .andExpect(status().isBadRequest());

        verify(habilitadoService, never()).createBulk(any());
    }

    /** El borde: exactamente 500 SI se acepta. */
    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void crearMasivo_conExactamente500_seAcepta() throws Exception {
        List<CreateHabilitadoRequest> loteAlLimite = IntStream.range(0, 500)
            .mapToObj(i -> {
                CreateHabilitadoRequest req = new CreateHabilitadoRequest();
                req.setDocumento(String.valueOf(30000000 + i));
                req.setApellido("Apellido" + i);
                return req;
            })
            .toList();
        when(habilitadoService.createBulk(any())).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(post("/admin/habilitados/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loteAlLimite)))
            .andExpect(status().isOk());

        verify(habilitadoService).createBulk(any());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void crearMasivo_conListaVacia_rechaza() throws Exception {
        mockMvc.perform(post("/admin/habilitados/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[]"))
            .andExpect(status().isBadRequest());

        verify(habilitadoService, never()).createBulk(any());
    }

    /**
     * Los elementos DE ADENTRO de la lista tambien se validan (el @Valid del
     * List<@Valid ...>). Un documento con formato invalido no puede colarse en
     * un lote aunque el lote tenga tamanio correcto: si entrara, esa fila del
     * padron seria inconsumible desde /auth/register, que valida el mismo
     * formato.
     */
    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void crearMasivo_conUnDocumentoInvalidoEnElLote_rechazaTodo() throws Exception {
        CreateHabilitadoRequest valido = validRequest();
        CreateHabilitadoRequest invalido = new CreateHabilitadoRequest();
        invalido.setDocumento("abc");  // no son 7-9 digitos
        invalido.setApellido("Pérez");

        mockMvc.perform(post("/admin/habilitados/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(valido, invalido))))
            .andExpect(status().isBadRequest());

        verify(habilitadoService, never()).createBulk(any());
    }

    // ------------------------------------------------------------------
    // 4. Validacion del alta simple
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void crear_conDocumentoDeFormatoInvalido_rechaza() throws Exception {
        CreateHabilitadoRequest req = validRequest();
        req.setDocumento("123");  // menos de 7 digitos

        mockMvc.perform(post("/admin/habilitados")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());

        verify(habilitadoService, never()).create(any());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void crear_sinApellido_rechaza() throws Exception {
        CreateHabilitadoRequest req = validRequest();
        req.setApellido(null);

        mockMvc.perform(post("/admin/habilitados")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());

        verify(habilitadoService, never()).create(any());
    }
}
