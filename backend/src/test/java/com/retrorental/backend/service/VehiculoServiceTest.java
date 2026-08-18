package com.retrorental.backend.service;

import com.retrorental.backend.dto.request.CreateVehiculoRequest;
import com.retrorental.backend.dto.request.UpdateVehiculoRequest;
import com.retrorental.backend.dto.response.VehiculoResponse;
import com.retrorental.backend.exception.ConflictException;
import com.retrorental.backend.exception.ErrorCode;
import com.retrorental.backend.exception.ForbiddenException;
import com.retrorental.backend.exception.ResourceNotFoundException;
import com.retrorental.backend.model.Administrador;
import com.retrorental.backend.model.Empleado;
import com.retrorental.backend.model.Vehiculo;
import com.retrorental.backend.model.enums.Estado;
import com.retrorental.backend.model.enums.TipoCombustible;
import com.retrorental.backend.model.enums.TipoVehiculo;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas de la asignación self-service de vehículos y del ABM.
 *
 * POR QUE ESTE ARCHIVO. `VehiculoService` no tenía un solo test unitario (ver
 * docs/BACKEND-AUDIT.md, TST-01) y es la clase con más lógica de ESTADO CRUZADO
 * del backend: la máquina de tomar/liberar decide quién puede cargarle
 * combustible a qué máquina.
 *
 * Un bug acá no tira la aplicación: deja dos empleados "dueños" del mismo
 * vehículo, o permite que alguien libere una máquina que no tiene. Son errores
 * que no hacen ruido y que aparecen como datos raros semanas después.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("vehiculo")
class VehiculoServiceTest {

    @Mock private PersonaRepository personaRepository;
    @Mock private VehiculoRepository vehiculoRepository;

    private VehiculoService service;

    private Empleado juan;
    private Empleado pedro;
    private Vehiculo vehiculo;

    @BeforeEach
    void setUp() {
        service = new VehiculoService(personaRepository, vehiculoRepository);

        juan = empleado(1, "jperez");
        pedro = empleado(2, "pgomez");

        vehiculo = new Vehiculo();
        vehiculo.setId(10);
        vehiculo.setIdentificador("AA123BB");
        vehiculo.setModelo("Hilux");
        vehiculo.setTipoVehiculo(TipoVehiculo.CAMIONETA);
        vehiculo.setTipoCombustible(TipoCombustible.GASOIL_GRADO_2);
        vehiculo.setCapacidadTanque(80);
        vehiculo.setEstado(Estado.DISPONIBLE);
        vehiculo.setUsoAcumulado(1000);
        vehiculo.setConsumoPromedio(new BigDecimal("12.00"));
        vehiculo.setFechaUltimoMantenimiento(LocalDate.now());

        when(vehiculoRepository.findById(10)).thenReturn(Optional.of(vehiculo));
        when(vehiculoRepository.save(any(Vehiculo.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(personaRepository.findByUsername("jperez")).thenReturn(Optional.of(juan));
        when(personaRepository.findByUsername("pgomez")).thenReturn(Optional.of(pedro));
    }

    // ------------------------------------------------------------------
    // tomar() — la máquina de estados
    // ------------------------------------------------------------------

    @Test
    void tomar_vehiculoLibre_loAsignaYLoPasaAEnUso() {
        VehiculoResponse res = service.tomar("jperez", 10);

        assertThat(vehiculo.getOperario()).isEqualTo(juan);
        assertThat(vehiculo.getEstado()).isEqualTo(Estado.EN_USO);
        assertThat(res.idOperario()).isEqualTo(1);
        verify(vehiculoRepository).save(vehiculo);
    }

    /**
     * Idempotencia: tomar dos veces el MISMO vehículo con el MISMO empleado no
     * falla. Importa porque la app puede reintentar el request (red mala en el
     * yacimiento) y un 409 dejaría al empleado creyendo que no lo tiene.
     */
    @Test
    void tomar_dosVecesElMismoEmpleado_noFalla() {
        service.tomar("jperez", 10);
        VehiculoResponse segunda = service.tomar("jperez", 10);

        assertThat(segunda.idOperario()).isEqualTo(1);
        assertThat(vehiculo.getOperario()).isEqualTo(juan);
    }

    /**
     * EL caso que justifica este archivo: dos empleados NO pueden tener el mismo
     * vehículo. Si esto se rompiera, dos personas cargarían combustible sobre la
     * misma máquina creyendo cada una que es suya, y el odómetro quedaría
     * intercalado entre dos usos distintos.
     */
    @Test
    void tomar_vehiculoTomadoPorOtro_rechazaConConflicto() {
        vehiculo.setOperario(pedro);

        assertThatThrownBy(() -> service.tomar("jperez", 10))
            .isInstanceOf(ConflictException.class)
            .hasFieldOrPropertyWithValue("code", ErrorCode.VEHICULO_ALREADY_ASSIGNED);

        assertThat(vehiculo.getOperario())
            .as("el vehiculo tiene que seguir siendo de quien lo tenia")
            .isEqualTo(pedro);
    }

    @Test
    void tomar_vehiculoEnMantenimiento_rechaza() {
        vehiculo.setEstado(Estado.EN_MANTENIMIENTO);

        assertThatThrownBy(() -> service.tomar("jperez", 10))
            .isInstanceOf(ConflictException.class)
            .hasFieldOrPropertyWithValue("code", ErrorCode.VEHICULO_NOT_AVAILABLE);

        assertThat(vehiculo.getOperario()).isNull();
    }

    @Test
    void tomar_vehiculoDadoDeBaja_rechaza() {
        vehiculo.setFechaBaja(LocalDate.now());

        assertThatThrownBy(() -> service.tomar("jperez", 10))
            .isInstanceOf(ConflictException.class)
            .hasFieldOrPropertyWithValue("code", ErrorCode.VEHICULO_ALREADY_INACTIVE);
    }

    @Test
    void tomar_vehiculoInexistente_devuelve404() {
        when(vehiculoRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.tomar("jperez", 99))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasFieldOrPropertyWithValue("code", ErrorCode.VEHICULO_NOT_FOUND);
    }

    /**
     * Un administrador puede cargar tickets, pero no "toma" vehículos: el pool
     * es de empleados. `Vehiculo.operario` es de tipo Empleado, así que dejar
     * entrar a un admin acá ni siquiera compilaría en el modelo.
     */
    @Test
    void tomar_comoAdministrador_rechaza() {
        Administrador admin = new Administrador();
        admin.setId(3);
        admin.setUsername("jefe");
        when(personaRepository.findByUsername("jefe")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.tomar("jefe", 10))
            .isInstanceOf(ForbiddenException.class)
            .hasFieldOrPropertyWithValue("code", ErrorCode.NOT_EMPLOYEE);
    }

    // ------------------------------------------------------------------
    // liberar()
    // ------------------------------------------------------------------

    @Test
    void liberar_elDueno_loLiberaYVuelveADisponible() {
        vehiculo.setOperario(juan);
        vehiculo.setEstado(Estado.EN_USO);

        service.liberar("jperez", 10);

        assertThat(vehiculo.getOperario()).isNull();
        assertThat(vehiculo.getEstado()).isEqualTo(Estado.DISPONIBLE);
        verify(vehiculoRepository).save(vehiculo);
    }

    /**
     * Nadie puede liberar un vehículo ajeno. Sin este freno, cualquier empleado
     * podría "destomar" la máquina de otro y quedarse con ella.
     */
    @Test
    void liberar_vehiculoDeOtroEmpleado_rechaza() {
        vehiculo.setOperario(pedro);
        vehiculo.setEstado(Estado.EN_USO);

        assertThatThrownBy(() -> service.liberar("jperez", 10))
            .isInstanceOf(ForbiddenException.class)
            .hasFieldOrPropertyWithValue("code", ErrorCode.VEHICULO_NOT_ASSIGNED);

        assertThat(vehiculo.getOperario()).isEqualTo(pedro);
        verify(vehiculoRepository, never()).save(any());
    }

    @Test
    void liberar_vehiculoSinOperario_rechaza() {
        vehiculo.setOperario(null);

        assertThatThrownBy(() -> service.liberar("jperez", 10))
            .isInstanceOf(ForbiddenException.class)
            .hasFieldOrPropertyWithValue("code", ErrorCode.VEHICULO_NOT_ASSIGNED);
    }

    // ------------------------------------------------------------------
    // create() / update() — unicidad y normalización del identificador
    // ------------------------------------------------------------------

    @Test
    void create_identificadorRepetido_rechaza() {
        when(vehiculoRepository.existsByIdentificador("AA123BB")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest("AA123BB")))
            .isInstanceOf(ConflictException.class)
            .hasFieldOrPropertyWithValue("code", ErrorCode.IDENTIFICADOR_ALREADY_EXISTS);

        verify(vehiculoRepository, never()).save(any());
    }

    /**
     * El identificador entra en una constraint UNIQUE, así que los espacios de
     * los bordes tienen que recortarse ANTES de comparar: sin esto "M-01" y
     * "M-01 " serían dos vehículos distintos para la base y el mismo para
     * cualquier persona que los mire.
     */
    @Test
    void create_recortaLosEspaciosDelIdentificadorAntesDeChequearUnicidad() {
        when(vehiculoRepository.existsByIdentificador("M-01")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest("  M-01  ")))
            .isInstanceOf(ConflictException.class);

        verify(vehiculoRepository).existsByIdentificador("M-01");
    }

    /**
     * El alta guarda la estimación de consumo DOS veces: en consumoPromedio (que
     * el cálculo real va a ir pisando) y en consumoInicial, que no se toca nunca
     * más. Es el valor al que se vuelve si se anulan cargas hasta quedarse sin
     * datos para calcular.
     */
    @Test
    void create_guardaLaEstimacionTambienComoConsumoInicial() {
        when(vehiculoRepository.existsByIdentificador(any())).thenReturn(false);

        service.create(createRequest("NUEVO-1"));

        verify(vehiculoRepository).save(org.mockito.ArgumentMatchers.argThat(v ->
            v.getConsumoPromedio().compareTo(new BigDecimal("15.50")) == 0
                && v.getConsumoInicial().compareTo(new BigDecimal("15.50")) == 0));
    }

    @Test
    void create_sinEstado_quedaDisponible() {
        when(vehiculoRepository.existsByIdentificador(any())).thenReturn(false);
        CreateVehiculoRequest req = createRequest("NUEVO-2");
        req.setEstado(null);

        service.create(req);

        verify(vehiculoRepository).save(org.mockito.ArgumentMatchers.argThat(v ->
            v.getEstado() == Estado.DISPONIBLE));
    }

    /**
     * Al editar, el identificador puede quedar igual: la colisión se chequea
     * contra OTRO vehículo, no contra sí mismo. Sin el filtro por id, editar el
     * color de un vehículo sin tocarle la patente daría 409.
     */
    @Test
    void update_conservandoSuPropioIdentificador_noEsColision() {
        when(vehiculoRepository.findByIdentificador("AA123BB")).thenReturn(Optional.of(vehiculo));

        service.update(10, updateRequest("AA123BB"));

        verify(vehiculoRepository).save(vehiculo);
    }

    @Test
    void update_conIdentificadorDeOtroVehiculo_rechaza() {
        Vehiculo otro = new Vehiculo();
        otro.setId(11);
        otro.setIdentificador("CC456DD");
        when(vehiculoRepository.findByIdentificador("CC456DD")).thenReturn(Optional.of(otro));

        assertThatThrownBy(() -> service.update(10, updateRequest("CC456DD")))
            .isInstanceOf(ConflictException.class)
            .hasFieldOrPropertyWithValue("code", ErrorCode.IDENTIFICADOR_ALREADY_EXISTS);
    }

    /**
     * La edición NO toca consumoInicial: el formulario del admin viene precargado
     * con el consumo CALCULADO, así que guardarlo ahí convertiría un número
     * derivado en la supuesta estimación del alta.
     */
    @Test
    void update_noPisaElConsumoInicial() {
        vehiculo.setConsumoInicial(new BigDecimal("12.00"));
        when(vehiculoRepository.findByIdentificador(any())).thenReturn(Optional.empty());

        service.update(10, updateRequest("AA123BB"));

        assertThat(vehiculo.getConsumoInicial()).isEqualByComparingTo("12.00");
    }

    // ------------------------------------------------------------------
    // desactivar()
    // ------------------------------------------------------------------

    /**
     * Dar de baja un vehículo LIBERA la asignación. Si no lo hiciera, seguiría
     * apareciendo en la flota del empleado que lo tenía y este podría intentar
     * cargarle tickets a una máquina que ya no existe operativamente.
     */
    @Test
    void desactivar_liberaLaAsignacion() {
        vehiculo.setOperario(juan);

        service.desactivar(10);

        assertThat(vehiculo.getOperario()).isNull();
        assertThat(vehiculo.getFechaBaja()).isNotNull();
    }

    @Test
    void desactivar_vehiculoYaDadoDeBaja_rechaza() {
        vehiculo.setFechaBaja(LocalDate.now());

        assertThatThrownBy(() -> service.desactivar(10))
            .isInstanceOf(ConflictException.class)
            .hasFieldOrPropertyWithValue("code", ErrorCode.VEHICULO_ALREADY_INACTIVE);
    }

    // ------------------------------------------------------------------
    // listAsignados()
    // ------------------------------------------------------------------

    @Test
    void listAsignados_devuelveLosDelEmpleado() {
        juan.setVehiculos(List.of(vehiculo));

        List<VehiculoResponse> asignados = service.listAsignados("jperez");

        assertThat(asignados).hasSize(1);
        assertThat(asignados.get(0).identificador()).isEqualTo("AA123BB");
    }

    /** Sin vehículos asignados devuelve lista vacía, no null ni error. */
    @Test
    void listAsignados_sinVehiculos_devuelveListaVacia() {
        juan.setVehiculos(null);

        assertThat(service.listAsignados("jperez")).isEmpty();
    }

    @Test
    void listAsignados_comoAdministrador_rechaza() {
        Administrador admin = new Administrador();
        admin.setId(3);
        admin.setUsername("jefe");
        when(personaRepository.findByUsername("jefe")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.listAsignados("jefe"))
            .isInstanceOf(ForbiddenException.class)
            .hasFieldOrPropertyWithValue("code", ErrorCode.NOT_EMPLOYEE);
    }

    @Test
    void listAsignados_usuarioInexistente_devuelve404() {
        when(personaRepository.findByUsername("fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listAsignados("fantasma"))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasFieldOrPropertyWithValue("code", ErrorCode.USER_NOT_FOUND);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Empleado empleado(Integer id, String username) {
        Empleado e = new Empleado();
        e.setId(id);
        e.setUsername(username);
        e.setNombre("Nombre" + id);
        e.setApellido("Apellido" + id);
        return e;
    }

    private CreateVehiculoRequest createRequest(String identificador) {
        CreateVehiculoRequest req = new CreateVehiculoRequest();
        req.setIdentificador(identificador);
        req.setModelo("Hilux");
        req.setTipoVehiculo(TipoVehiculo.CAMIONETA);
        req.setTipoCombustible(TipoCombustible.GASOIL_GRADO_2);
        req.setCapacidadTanque(80);
        req.setEstado(Estado.DISPONIBLE);
        req.setFechaUltimoMantenimiento(LocalDate.now());
        req.setUsoAcumulado(1000);
        req.setConsumoPromedio(new BigDecimal("15.50"));
        return req;
    }

    private UpdateVehiculoRequest updateRequest(String identificador) {
        UpdateVehiculoRequest req = new UpdateVehiculoRequest();
        req.setIdentificador(identificador);
        req.setModelo("Hilux SRV");
        req.setTipoVehiculo(TipoVehiculo.CAMIONETA);
        req.setTipoCombustible(TipoCombustible.GASOIL_GRADO_2);
        req.setCapacidadTanque(80);
        req.setEstado(Estado.DISPONIBLE);
        req.setFechaUltimoMantenimiento(LocalDate.now());
        req.setUsoAcumulado(1200);
        req.setConsumoPromedio(new BigDecimal("13.00"));
        return req;
    }
}
