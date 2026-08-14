package com.retrorental.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.retrorental.backend.dto.request.CreateHabilitadoRequest;
import com.retrorental.backend.dto.response.HabilitadoResponse;
import com.retrorental.backend.exception.ConflictException;
import com.retrorental.backend.exception.ErrorCode;
import com.retrorental.backend.exception.ForbiddenException;
import com.retrorental.backend.exception.ResourceNotFoundException;
import com.retrorental.backend.model.Empleado;
import com.retrorental.backend.model.EmpleadoHabilitado;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.retrorental.backend.repository.EmpleadoHabilitadoRepository;

/**
 * El padron es un control de SEGURIDAD: es lo unico que separa "los empleados
 * se registran solos" de "cualquiera en internet se registra solo". Por eso se
 * testea cada forma de rechazo por separado, incluida la que verifica que los
 * tres rechazos sean indistinguibles entre si.
 */
@ExtendWith(MockitoExtension.class)
class HabilitadoServiceTest {

    @Mock private EmpleadoHabilitadoRepository habilitadoRepository;

    @InjectMocks private HabilitadoService service;

    private EmpleadoHabilitado habilitado() {
        EmpleadoHabilitado h = new EmpleadoHabilitado();
        h.setId(1);
        h.setDocumento("30111222");
        h.setApellido("Pérez");
        h.setNombre("Juan");
        h.setFechaAlta(LocalDate.now());
        return h;
    }

    // -----------------------------------------------------------------------
    // validarHabilitacion: el filtro que sostiene todo
    // -----------------------------------------------------------------------

    @Test
    void validarHabilitacion_aceptaDocumentoEnPadronConApellidoCoincidente() {
        when(habilitadoRepository.findByDocumento("30111222"))
            .thenReturn(Optional.of(habilitado()));

        EmpleadoHabilitado resultado = service.validarHabilitacion("30111222", "Pérez");

        assertNotNull(resultado);
        assertEquals("30111222", resultado.getDocumento());
    }

    @Test
    void validarHabilitacion_rechazaDocumentoFueraDelPadron() {
        when(habilitadoRepository.findByDocumento("99999999")).thenReturn(Optional.empty());

        ForbiddenException ex = assertThrows(ForbiddenException.class,
            () -> service.validarHabilitacion("99999999", "Perez"));

        assertEquals(ErrorCode.REGISTRO_NO_HABILITADO, ex.getCode());
    }

    @Test
    void validarHabilitacion_rechazaApellidoQueNoCoincide() {
        when(habilitadoRepository.findByDocumento("30111222"))
            .thenReturn(Optional.of(habilitado()));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
            () -> service.validarHabilitacion("30111222", "Gomez"));

        assertEquals(ErrorCode.REGISTRO_NO_HABILITADO, ex.getCode());
    }

    @Test
    void validarHabilitacion_rechazaHabilitacionYaConsumida() {
        EmpleadoHabilitado usada = habilitado();
        usada.setFechaUso(LocalDateTime.now());
        usada.setPersona(new Empleado());
        when(habilitadoRepository.findByDocumento("30111222")).thenReturn(Optional.of(usada));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
            () -> service.validarHabilitacion("30111222", "Pérez"));

        assertEquals(ErrorCode.REGISTRO_NO_HABILITADO, ex.getCode());
    }

    /**
     * Si los mensajes difirieran, el endpoint publico se volveria un oraculo
     * para averiguar que documentos pertenecen al personal del cliente.
     */
    @Test
    void validarHabilitacion_losTresRechazosSonIndistinguibles() {
        EmpleadoHabilitado usada = habilitado();
        usada.setFechaUso(LocalDateTime.now());

        when(habilitadoRepository.findByDocumento("99999999")).thenReturn(Optional.empty());
        when(habilitadoRepository.findByDocumento("30111222"))
            .thenReturn(Optional.of(habilitado()));
        when(habilitadoRepository.findByDocumento("30333444")).thenReturn(Optional.of(usada));

        ForbiddenException fueraDelPadron = assertThrows(ForbiddenException.class,
            () -> service.validarHabilitacion("99999999", "Perez"));
        ForbiddenException apellidoMalo = assertThrows(ForbiddenException.class,
            () -> service.validarHabilitacion("30111222", "Gomez"));
        ForbiddenException yaUsada = assertThrows(ForbiddenException.class,
            () -> service.validarHabilitacion("30333444", "Pérez"));

        assertEquals(fueraDelPadron.getMessage(), apellidoMalo.getMessage());
        assertEquals(fueraDelPadron.getMessage(), yaUsada.getMessage());
        assertEquals(fueraDelPadron.getCode(), apellidoMalo.getCode());
        assertEquals(fueraDelPadron.getCode(), yaUsada.getCode());
    }

    // -----------------------------------------------------------------------
    // Normalizacion del apellido: tolerante con el empleado, no con el atacante
    // -----------------------------------------------------------------------

    @Test
    void validarHabilitacion_toleraMayusculasAcentosYEspacios() {
        when(habilitadoRepository.findByDocumento("30111222"))
            .thenReturn(Optional.of(habilitado()));

        assertNotNull(service.validarHabilitacion("30111222", "perez"));
        assertNotNull(service.validarHabilitacion("30111222", "PEREZ"));
        assertNotNull(service.validarHabilitacion("30111222", "  Pérez  "));
        assertNotNull(service.validarHabilitacion("30111222", "PÉREZ"));
    }

    @Test
    void validarHabilitacion_toleraApellidoCompuestoConEspaciosDeMas() {
        EmpleadoHabilitado h = habilitado();
        h.setApellido("Di  Marco");
        when(habilitadoRepository.findByDocumento("30111222")).thenReturn(Optional.of(h));

        assertNotNull(service.validarHabilitacion("30111222", "di marco"));
    }

    // -----------------------------------------------------------------------
    // Consumo
    // -----------------------------------------------------------------------

    @Test
    void marcarUsado_registraElMomentoYLaPersona() {
        EmpleadoHabilitado h = habilitado();
        Empleado persona = new Empleado();
        persona.setId(7);

        service.marcarUsado(h, persona);

        assertTrue(h.estaUsada());
        assertNotNull(h.getFechaUso());
        assertEquals(persona, h.getPersona());
        verify(habilitadoRepository).save(h);
    }

    @Test
    void marcarUsadoSiExiste_noPisaUnaHabilitacionYaConsumida() {
        EmpleadoHabilitado usada = habilitado();
        LocalDateTime usoOriginal = LocalDateTime.now().minusDays(3);
        usada.setFechaUso(usoOriginal);
        when(habilitadoRepository.findByDocumento("30111222")).thenReturn(Optional.of(usada));

        service.marcarUsadoSiExiste("30111222", new Empleado());

        assertEquals(usoOriginal, usada.getFechaUso());
        verify(habilitadoRepository, never()).save(any());
    }

    @Test
    void marcarUsadoSiExiste_noFallaSiElDocumentoNoEstaEnElPadron() {
        when(habilitadoRepository.findByDocumento("99999999")).thenReturn(Optional.empty());

        service.marcarUsadoSiExiste("99999999", new Empleado());

        verify(habilitadoRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // ABM del administrador
    // -----------------------------------------------------------------------

    @Test
    void create_rechazaDocumentoYaPresenteEnElPadron() {
        when(habilitadoRepository.existsByDocumento("30111222")).thenReturn(true);

        CreateHabilitadoRequest request = new CreateHabilitadoRequest();
        request.setDocumento("30111222");
        request.setApellido("Pérez");

        ConflictException ex = assertThrows(ConflictException.class, () -> service.create(request));

        assertEquals(ErrorCode.HABILITADO_ALREADY_EXISTS, ex.getCode());
        verify(habilitadoRepository, never()).save(any());
    }

    @Test
    void createBulk_salteaLosRepetidosEnLugarDeAbortarTodo() {
        when(habilitadoRepository.existsByDocumento("30111222")).thenReturn(true);
        when(habilitadoRepository.existsByDocumento("30555666")).thenReturn(false);
        when(habilitadoRepository.findAllByOrderByFechaAltaDesc()).thenReturn(List.of());

        CreateHabilitadoRequest repetido = new CreateHabilitadoRequest();
        repetido.setDocumento("30111222");
        repetido.setApellido("Pérez");

        CreateHabilitadoRequest nuevo = new CreateHabilitadoRequest();
        nuevo.setDocumento("30555666");
        nuevo.setApellido("Gómez");

        service.createBulk(List.of(repetido, nuevo));

        verify(habilitadoRepository).save(any(EmpleadoHabilitado.class));
    }

    @Test
    void delete_quitaUnaHabilitacionSinUsar() {
        EmpleadoHabilitado h = habilitado();
        when(habilitadoRepository.findById(1)).thenReturn(Optional.of(h));

        service.delete(1);

        verify(habilitadoRepository).delete(h);
    }

    /**
     * Borrar la fila no le saca el acceso a quien ya se registro (su password y
     * su token siguen siendo validos) y ademas destruye la trazabilidad de
     * quien autorizo esa alta.
     */
    @Test
    void delete_rechazaSiLaHabilitacionYaSeUso() {
        EmpleadoHabilitado usada = habilitado();
        usada.setFechaUso(LocalDateTime.now());
        when(habilitadoRepository.findById(1)).thenReturn(Optional.of(usada));

        ConflictException ex = assertThrows(ConflictException.class, () -> service.delete(1));

        assertEquals(ErrorCode.HABILITADO_ALREADY_USED, ex.getCode());
        verify(habilitadoRepository, never()).delete(any());
    }

    @Test
    void delete_fallaSiLaHabilitacionNoExiste() {
        when(habilitadoRepository.findById(99)).thenReturn(Optional.empty());

        ResourceNotFoundException ex =
            assertThrows(ResourceNotFoundException.class, () -> service.delete(99));

        assertEquals(ErrorCode.HABILITADO_NOT_FOUND, ex.getCode());
    }

    @Test
    void listAll_exponeElEstadoResueltoDesdeElBackend() {
        EmpleadoHabilitado libre = habilitado();
        EmpleadoHabilitado usada = habilitado();
        usada.setId(2);
        usada.setDocumento("30555666");
        usada.setFechaUso(LocalDateTime.now());
        Empleado persona = new Empleado();
        persona.setUsername("jperez");
        usada.setPersona(persona);

        when(habilitadoRepository.findAllByOrderByFechaAltaDesc())
            .thenReturn(List.of(libre, usada));

        List<HabilitadoResponse> resultado = service.listAll();

        assertEquals(2, resultado.size());
        assertEquals(false, resultado.get(0).registrado());
        assertNull(resultado.get(0).username());
        assertEquals(true, resultado.get(1).registrado());
        assertEquals("jperez", resultado.get(1).username());
    }
}
