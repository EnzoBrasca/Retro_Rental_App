package com.retrorental.backend.service;

import com.retrorental.backend.dto.request.CreateEmpleadoRequest;
import com.retrorental.backend.dto.request.TelefonoRequest;
import com.retrorental.backend.dto.request.UpdateEmpleadoRequest;
import com.retrorental.backend.dto.response.EmpleadoResponse;
import com.retrorental.backend.exception.ConflictException;
import com.retrorental.backend.exception.ErrorCode;
import com.retrorental.backend.exception.ResourceNotFoundException;
import com.retrorental.backend.model.Administrador;
import com.retrorental.backend.model.Empleado;
import com.retrorental.backend.model.Vehiculo;
import com.retrorental.backend.model.embeddable.Telefono;
import com.retrorental.backend.model.enums.Rol;
import com.retrorental.backend.repository.PersonaRepository;
import com.retrorental.backend.repository.VehiculoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas del ABM de empleados.
 *
 * POR QUE ESTE ARCHIVO. `EmpleadoService` no tenía tests (ver
 * docs/BACKEND-AUDIT.md, TST-01) y tiene los dos efectos CRUZADOS más
 * silenciosos del backend:
 *
 * 1. `desactivar()` libera los vehículos que el empleado tenía asignados. Si
 *    fallara, esas máquinas quedarían "fantasma": apuntando a alguien que ya no
 *    trabaja, invisibles para el resto de la flota y sin que nadie pueda
 *    tomarlas.
 * 2. `create()` consume la fila del padrón si el documento estaba habilitado.
 *    Sin eso, un empleado que el propio jefe dio de alta le queda figurando en
 *    el padrón como "sin registrar", y encima su habilitación sigue disponible
 *    para que otro la use.
 *
 * Ninguno de los dos rompe nada visible cuando falla. Por eso hay que probarlos.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("empleado")
class EmpleadoServiceTest {

    @Mock private PersonaRepository personaRepository;
    @Mock private VehiculoRepository vehiculoRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UsernameGenerator usernameGenerator;
    @Mock private HabilitadoService habilitadoService;

    private EmpleadoService service;

    private Empleado empleado;

    @BeforeEach
    void setUp() {
        service = new EmpleadoService(
            personaRepository, vehiculoRepository, passwordEncoder,
            usernameGenerator, habilitadoService);

        empleado = new Empleado();
        empleado.setId(1);
        empleado.setNombre("Ana");
        empleado.setApellido("Gómez");
        empleado.setDocumento("30111222");
        empleado.setUsername("agomez");
        empleado.setRol(Rol.EMPLEADO);
        empleado.setFechaAlta(LocalDate.now());
        empleado.setTelefono(new Telefono("11", "55501234"));

        when(personaRepository.findById(1)).thenReturn(Optional.of(empleado));
        when(personaRepository.save(any(Empleado.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(vehiculoRepository.save(any(Vehiculo.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode(any())).thenReturn("$2a$hash");
        when(usernameGenerator.generate(any(), any())).thenReturn("agomez");
    }

    // ------------------------------------------------------------------
    // desactivar() — la cascada
    // ------------------------------------------------------------------

    /**
     * EL test que justifica este archivo. Dar de baja al empleado tiene que
     * soltar TODOS sus vehículos, no solo el primero.
     */
    @Test
    void desactivar_liberaTodosLosVehiculosAsignados() {
        Vehiculo uno = vehiculoDe(10, empleado);
        Vehiculo dos = vehiculoDe(11, empleado);
        Vehiculo tres = vehiculoDe(12, empleado);
        empleado.setVehiculos(new ArrayList<>(List.of(uno, dos, tres)));

        service.desactivar(1);

        assertThat(uno.getOperario()).isNull();
        assertThat(dos.getOperario()).isNull();
        assertThat(tres.getOperario())
            .as("el ultimo de la lista tambien tiene que quedar libre")
            .isNull();
        assertThat(empleado.getFechaBaja()).isNotNull();
    }

    @Test
    void desactivar_sinVehiculosAsignados_noFalla() {
        empleado.setVehiculos(null);

        service.desactivar(1);

        assertThat(empleado.getFechaBaja()).isNotNull();
        verify(vehiculoRepository, never()).save(any());
    }

    @Test
    void desactivar_empleadoYaDadoDeBaja_rechaza() {
        empleado.setFechaBaja(LocalDate.now().minusDays(1));

        assertThatThrownBy(() -> service.desactivar(1))
            .isInstanceOf(ConflictException.class)
            .hasFieldOrPropertyWithValue("code", ErrorCode.EMPLEADO_ALREADY_INACTIVE);
    }

    /**
     * Al rechazar por doble baja NO se puede haber tocado ningún vehículo: si la
     * liberación ocurriera antes del chequeo, un segundo intento de baja soltaría
     * vehículos que el empleado volvió a tomar legítimamente.
     */
    @Test
    void desactivar_dosVeces_noLiberaVehiculosEnElSegundoIntento() {
        Vehiculo v = vehiculoDe(10, empleado);
        empleado.setVehiculos(new ArrayList<>(List.of(v)));
        empleado.setFechaBaja(LocalDate.now().minusDays(1));

        assertThatThrownBy(() -> service.desactivar(1))
            .isInstanceOf(ConflictException.class);

        assertThat(v.getOperario()).isEqualTo(empleado);
        verify(vehiculoRepository, never()).save(any());
    }

    @Test
    void desactivar_empleadoInexistente_devuelve404() {
        when(personaRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.desactivar(99))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasFieldOrPropertyWithValue("code", ErrorCode.EMPLEADO_NOT_FOUND);
    }

    /**
     * Un administrador no es un empleado: el ABM de personal no puede darlo de
     * baja aunque compartan la tabla `personas` (herencia JOINED). Se responde
     * 404 y no 403 a propósito: para este ABM, ese id simplemente no existe.
     */
    @Test
    void desactivar_unAdministrador_devuelve404YNoLoDaDeBaja() {
        Administrador admin = new Administrador();
        admin.setId(2);
        when(personaRepository.findById(2)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.desactivar(2))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasFieldOrPropertyWithValue("code", ErrorCode.EMPLEADO_NOT_FOUND);

        verify(personaRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // create() — el efecto cruzado sobre el padrón
    // ------------------------------------------------------------------

    /**
     * El alta manual del admin NO pasa por el padrón, pero igual tiene que
     * consumir la fila si ese documento estaba habilitado. Si no, al jefe le
     * queda figurando como "sin registrar" un empleado que él mismo creó, y la
     * habilitación sigue libre para que la use un tercero.
     */
    @Test
    void create_consumeLaHabilitacionDelPadronSiExistia() {
        when(personaRepository.existsByDocumento("30111222")).thenReturn(false);

        service.create(createRequest("30111222"));

        verify(habilitadoService).marcarUsadoSiExiste(eq("30111222"), any(Empleado.class));
    }

    @Test
    void create_documentoRepetido_rechazaYNoTocaElPadron() {
        when(personaRepository.existsByDocumento("30111222")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest("30111222")))
            .isInstanceOf(ConflictException.class)
            .hasFieldOrPropertyWithValue("code", ErrorCode.DOCUMENTO_ALREADY_EXISTS);

        verify(personaRepository, never()).save(any());
        verify(habilitadoService, never()).marcarUsadoSiExiste(any(), any());
    }

    /**
     * El rol se fuerza a EMPLEADO en el servicio y no se acepta del request.
     * Es la misma garantía que en /auth/register (ver docs/SECURITY-AUDIT.md):
     * ningún camino de alta puede terminar creando un administrador.
     */
    @Test
    void create_fuerzaElRolEmpleado() {
        when(personaRepository.existsByDocumento(any())).thenReturn(false);

        service.create(createRequest("30111222"));

        verify(personaRepository).save(org.mockito.ArgumentMatchers.argThat(
            (Empleado e) -> e.getRol() == Rol.EMPLEADO));
    }

    /** La password nunca se guarda en claro. */
    @Test
    void create_guardaLaPasswordHasheada() {
        when(personaRepository.existsByDocumento(any())).thenReturn(false);

        service.create(createRequest("30111222"));

        verify(passwordEncoder).encode("secreto123");
        verify(personaRepository).save(org.mockito.ArgumentMatchers.argThat(
            (Empleado e) -> e.getPassword().equals("$2a$hash")));
    }

    @Test
    void create_autogeneraElUsername() {
        when(personaRepository.existsByDocumento(any())).thenReturn(false);
        when(usernameGenerator.generate("Ana", "Gómez")).thenReturn("agomez2");

        EmpleadoResponse res = service.create(createRequest("30111222"));

        assertThat(res.username()).isEqualTo("agomez2");
    }

    // ------------------------------------------------------------------
    // update()
    // ------------------------------------------------------------------

    /**
     * El username y el documento son inmutables desde el alta. La edición no los
     * toca aunque el request traiga otros datos: cambiar el username rompería el
     * login del empleado y el documento es su identidad contra el padrón.
     */
    @Test
    void update_noCambiaElUsernameNiElDocumento() {
        service.update(1, updateRequest());

        assertThat(empleado.getUsername()).isEqualTo("agomez");
        assertThat(empleado.getDocumento()).isEqualTo("30111222");
    }

    @Test
    void update_cambiaNombreApellidoYTelefono() {
        EmpleadoResponse res = service.update(1, updateRequest());

        assertThat(res.nombre()).isEqualTo("Ana María");
        assertThat(res.apellido()).isEqualTo("Gómez Pérez");
        assertThat(res.telefono().codigoArea()).isEqualTo("351");
        assertThat(res.telefono().numero()).isEqualTo("55599999");
    }

    @Test
    void update_empleadoInexistente_devuelve404() {
        when(personaRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99, updateRequest()))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasFieldOrPropertyWithValue("code", ErrorCode.EMPLEADO_NOT_FOUND);
    }

    // ------------------------------------------------------------------
    // listAll()
    // ------------------------------------------------------------------

    /** El listado incluye los dados de baja: es un ABM de gestión, no un catálogo. */
    @Test
    void listAll_incluyeLosDadosDeBaja() {
        Empleado baja = new Empleado();
        baja.setId(2);
        baja.setNombre("Luis");
        baja.setApellido("Díaz");
        baja.setDocumento("30999888");
        baja.setUsername("ldiaz");
        baja.setFechaAlta(LocalDate.now().minusYears(1));
        baja.setFechaBaja(LocalDate.now());
        when(personaRepository.findAllByOrderByFechaAltaDesc())
            .thenReturn(List.of(empleado, baja));

        List<EmpleadoResponse> todos = service.listAll();

        assertThat(todos).hasSize(2);
        assertThat(todos).anyMatch(e -> e.fechaBaja() != null);
    }

    /** Un empleado sin teléfono no rompe el mapeo (columna legacy nullable). */
    @Test
    void listAll_empleadoSinTelefono_noRompe() {
        empleado.setTelefono(null);
        when(personaRepository.findAllByOrderByFechaAltaDesc()).thenReturn(List.of(empleado));

        assertThat(service.listAll().get(0).telefono()).isNull();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Vehiculo vehiculoDe(Integer id, Empleado operario) {
        Vehiculo v = new Vehiculo();
        v.setId(id);
        v.setIdentificador("ID-" + id);
        v.setOperario(operario);
        return v;
    }

    private CreateEmpleadoRequest createRequest(String documento) {
        CreateEmpleadoRequest req = new CreateEmpleadoRequest();
        req.setNombre("Ana");
        req.setApellido("Gómez");
        req.setDocumento(documento);
        req.setPassword("secreto123");
        TelefonoRequest tel = new TelefonoRequest();
        tel.setCodigoArea("11");
        tel.setNumero("55501234");
        req.setTelefono(tel);
        return req;
    }

    private UpdateEmpleadoRequest updateRequest() {
        UpdateEmpleadoRequest req = new UpdateEmpleadoRequest();
        req.setNombre("Ana María");
        req.setApellido("Gómez Pérez");
        TelefonoRequest tel = new TelefonoRequest();
        tel.setCodigoArea("351");
        tel.setNumero("55599999");
        req.setTelefono(tel);
        return req;
    }
}
