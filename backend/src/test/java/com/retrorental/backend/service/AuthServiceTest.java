package com.retrorental.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.retrorental.backend.dto.request.RegisterRequest;
import com.retrorental.backend.dto.request.TelefonoRequest;
import com.retrorental.backend.dto.response.AuthResponse;
import com.retrorental.backend.exception.ErrorCode;
import com.retrorental.backend.exception.ForbiddenException;
import com.retrorental.backend.model.Empleado;
import com.retrorental.backend.model.EmpleadoHabilitado;
import com.retrorental.backend.model.Persona;
import com.retrorental.backend.model.enums.Rol;
import com.retrorental.backend.repository.PersonaRepository;
import com.retrorental.backend.security.JwtUtil;
import com.retrorental.backend.security.SecurityEventLogger;
import java.time.LocalDate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Registro publico: es el unico endpoint de escritura sin autenticar de la API.
 * Lo que se cubre aca no es funcionalidad, es la frontera de confianza.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("auth")
class AuthServiceTest {

    @Mock private PersonaRepository personaRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private UsernameGenerator usernameGenerator;
    @Mock private HabilitadoService habilitadoService;
    // El registro de eventos de seguridad no cambia el resultado del login, pero
    // sin el mock el servicio explota con NPE al intentar anotar un rechazo.
    @Mock private SecurityEventLogger securityEventLogger;

    @InjectMocks private AuthService service;

    private RegisterRequest request() {
        TelefonoRequest telefono = new TelefonoRequest();
        telefono.setCodigoArea("11");
        telefono.setNumero("55512345");

        RegisterRequest request = new RegisterRequest();
        request.setNombre("Juan");
        request.setApellido("Pérez");
        request.setDocumento("30111222");
        request.setPassword("unaPasswordLarga");
        request.setTelefono(telefono);
        return request;
    }

    private void stubAltaExitosa() {
        when(habilitadoService.validarHabilitacion(anyString(), anyString()))
            .thenReturn(new EmpleadoHabilitado());
        when(personaRepository.existsByDocumento(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hash");
        when(usernameGenerator.generate(anyString(), anyString())).thenReturn("jperez");
        when(personaRepository.save(any(Persona.class))).thenAnswer(i -> i.getArgument(0));
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("token");
        when(jwtUtil.extractExpirationMillis(anyString())).thenReturn(1L);
    }

    @Test
    void register_creaLaCuentaCuandoElDocumentoEstaHabilitado() {
        stubAltaExitosa();

        AuthResponse response = service.register(request());

        assertNotNull(response);
        assertEquals("jperez", response.getUsername());
        assertEquals(Rol.EMPLEADO, response.getRol());
        verify(personaRepository).save(any(Persona.class));
    }

    /**
     * El corazon del control: sin padron, este mismo request creaba una cuenta
     * valida para cualquier persona de internet.
     */
    @Test
    void register_rechazaDocumentoQueNoEstaEnElPadron() {
        when(habilitadoService.validarHabilitacion(anyString(), anyString()))
            .thenThrow(new ForbiddenException(ErrorCode.REGISTRO_NO_HABILITADO, "No podés registrarte"));

        assertThrows(ForbiddenException.class, () -> service.register(request()));

        verify(personaRepository, never()).save(any(Persona.class));
    }

    /**
     * El padron se valida ANTES que el duplicado. Si fuera al reves, el 409 del
     * duplicado delataria que ese documento tiene cuenta para CUALQUIER numero
     * probado, convirtiendo el endpoint en un enumerador de personal.
     */
    @Test
    void register_validaElPadronAntesQueElDocumentoDuplicado() {
        when(habilitadoService.validarHabilitacion(anyString(), anyString()))
            .thenThrow(new ForbiddenException(ErrorCode.REGISTRO_NO_HABILITADO, "No podés registrarte"));
        when(personaRepository.existsByDocumento(anyString())).thenReturn(true);

        ForbiddenException ex =
            assertThrows(ForbiddenException.class, () -> service.register(request()));

        assertEquals(ErrorCode.REGISTRO_NO_HABILITADO, ex.getCode());
        verify(personaRepository, never()).existsByDocumento(anyString());
    }

    /**
     * Regla que no admite excepciones: documento que ya tiene cuenta se rechaza
     * SIEMPRE. Es lo que hace estructuralmente imposible que un registro se
     * apropie de una cuenta ajena, este o no habilitado el documento.
     */
    @Test
    void register_rechazaDocumentoDuplicadoAunqueEsteHabilitado() {
        when(habilitadoService.validarHabilitacion(anyString(), anyString()))
            .thenReturn(new EmpleadoHabilitado());
        when(personaRepository.existsByDocumento("30111222")).thenReturn(true);

        ForbiddenException ex =
            assertThrows(ForbiddenException.class, () -> service.register(request()));

        assertEquals(ErrorCode.REGISTRO_NO_HABILITADO, ex.getCode());
        verify(personaRepository, never()).save(any(Persona.class));
    }

    /**
     * SEC-10: el registro publico tiene UNA sola respuesta de fracaso.
     *
     * Si el rechazo por documento ya registrado se distinguiera del rechazo del
     * padron, el endpoint serviria para averiguar que documentos ya tienen
     * cuenta probando numeros. Este test falla si alguien vuelve a separarlos.
     */
    @Test
    void register_elRechazoPorDuplicadoEsIndistinguibleDelRechazoDelPadron() {
        // doThrow/doReturn y no when(...): re-stubear con when() INVOCA el mock,
        // que en el segundo caso ya estaria configurado para lanzar.
        //
        // Caso A: el padron rechaza.
        doThrow(new ForbiddenException(
                ErrorCode.REGISTRO_NO_HABILITADO, HabilitadoService.RECHAZO_REGISTRO))
            .when(habilitadoService).validarHabilitacion(anyString(), anyString());
        ForbiddenException delPadron =
            assertThrows(ForbiddenException.class, () -> service.register(request()));

        // Caso B: el padron acepta, pero el documento ya tiene cuenta.
        doReturn(new EmpleadoHabilitado())
            .when(habilitadoService).validarHabilitacion(anyString(), anyString());
        when(personaRepository.existsByDocumento(anyString())).thenReturn(true);
        ForbiddenException porDuplicado =
            assertThrows(ForbiddenException.class, () -> service.register(request()));

        assertEquals(delPadron.getCode(), porDuplicado.getCode());
        assertEquals(delPadron.getMessage(), porDuplicado.getMessage());
    }

    @Test
    void register_consumeLaHabilitacionReciénConLaCuentaYaCreada() {
        EmpleadoHabilitado habilitado = new EmpleadoHabilitado();
        when(habilitadoService.validarHabilitacion(anyString(), anyString()))
            .thenReturn(habilitado);
        when(personaRepository.existsByDocumento(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hash");
        when(usernameGenerator.generate(anyString(), anyString())).thenReturn("jperez");
        when(personaRepository.save(any(Persona.class))).thenAnswer(i -> i.getArgument(0));
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("token");
        when(jwtUtil.extractExpirationMillis(anyString())).thenReturn(1L);

        service.register(request());

        verify(habilitadoService).marcarUsado(any(EmpleadoHabilitado.class), any(Persona.class));
    }

    @Test
    void register_noConsumeLaHabilitacionSiElAltaFalla() {
        when(habilitadoService.validarHabilitacion(anyString(), anyString()))
            .thenReturn(new EmpleadoHabilitado());
        when(personaRepository.existsByDocumento(anyString())).thenReturn(true);

        assertThrows(ForbiddenException.class, () -> service.register(request()));

        verify(habilitadoService, never()).marcarUsado(any(), any());
    }

    /** El rol nunca sale del request: el registro publico siempre crea EMPLEADO. */
    @Test
    void register_siempreCreaEmpleadoNuncaAdministrador() {
        stubAltaExitosa();

        AuthResponse response = service.register(request());

        assertEquals(Rol.EMPLEADO, response.getRol());
    }

    /** El apellido que se valida contra el padron es el del request, sin tocar. */
    @Test
    void register_validaElPadronConElApellidoDelRequest() {
        stubAltaExitosa();

        service.register(request());

        verify(habilitadoService).validarHabilitacion("30111222", "Pérez");
    }

    // -----------------------------------------------------------------------
    // Login: no lo toco el cambio, pero cubre que el padron no lo haya aflojado
    // -----------------------------------------------------------------------

    @Test
    void login_rechazaEmpleadoDadoDeBaja() {
        Empleado empleado = new Empleado();
        empleado.setUsername("jperez");
        empleado.setPassword("$2a$10$hash");
        empleado.setRol(Rol.EMPLEADO);
        empleado.setFechaBaja(LocalDate.now());

        when(personaRepository.findByUsername("jperez"))
            .thenReturn(java.util.Optional.of(empleado));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        var login = new com.retrorental.backend.dto.request.LoginRequest();
        login.setUsername("jperez");
        login.setPassword("unaPasswordLarga");

        assertThrows(com.retrorental.backend.exception.InvalidCredentialsException.class,
            () -> service.login(login));
    }
}
