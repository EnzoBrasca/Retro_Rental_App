package com.retrorental.backend.service;

import com.retrorental.backend.TestcontainersConfiguration;
import com.retrorental.backend.model.Empleado;
import com.retrorental.backend.model.Vehiculo;
import com.retrorental.backend.model.embeddable.Telefono;
import com.retrorental.backend.model.enums.Estado;
import com.retrorental.backend.model.enums.Rol;
import com.retrorental.backend.model.enums.TipoCombustible;
import com.retrorental.backend.model.enums.TipoVehiculo;
import com.retrorental.backend.repository.PersonaRepository;
import com.retrorental.backend.repository.TicketRepository;
import com.retrorental.backend.repository.VehiculoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La liberación de vehículos al dar de baja un empleado, contra un Postgres
 * real.
 *
 * POR QUE HACE FALTA UNA BASE DE VERDAD. Esta cascada pasó de ser un bucle de
 * `save()` en memoria a UNA sentencia `UPDATE ... SET operario = NULL`
 * (ver docs/BACKEND-AUDIT.md, DB-08). Con el repositorio mockeado, el test
 * unitario solo puede verificar que el servicio DELEGUE; lo que no puede ver es
 * si el UPDATE toca las filas correctas.
 *
 * Y ese es justo el riesgo de una query de modificación escrita a mano: un
 * `WHERE` de más o de menos no rompe nada visible, simplemente libera vehículos
 * que no correspondía o deja asignados los que sí.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Tag("empleado")
class EmpleadoPersistenciaTest {

    @Autowired private EmpleadoService empleadoService;
    @Autowired private PersonaRepository personaRepository;
    @Autowired private VehiculoRepository vehiculoRepository;
    @Autowired private TicketRepository ticketRepository;

    @MockitoBean private MinioStorageService minioStorageService;

    private Empleado ana;
    private Empleado luis;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        vehiculoRepository.deleteAll();
        personaRepository.deleteAll();

        ana = personaRepository.save(empleado("30111222", "agomez", "Ana"));
        luis = personaRepository.save(empleado("30999888", "ldiaz", "Luis"));
    }

    /**
     * EL test: la baja libera TODOS los vehículos del empleado y NINGUNO de
     * otro. Los dos lados importan — soltar de más le sacaría la máquina a un
     * compañero que la está usando.
     */
    @Test
    @Transactional
    void desactivar_liberaSoloLosVehiculosDelEmpleadoDadoDeBaja() {
        Vehiculo unoDeAna = vehiculoGuardado("ANA-1", ana);
        Vehiculo dosDeAna = vehiculoGuardado("ANA-2", ana);
        Vehiculo tresDeAna = vehiculoGuardado("ANA-3", ana);
        Vehiculo deLuis = vehiculoGuardado("LUIS-1", luis);
        Vehiculo sinOperario = vehiculoGuardado("LIBRE-1", null);

        empleadoService.desactivar(ana.getId());

        assertThat(vehiculoRepository.findById(unoDeAna.getId()).orElseThrow().getOperario())
            .isNull();
        assertThat(vehiculoRepository.findById(dosDeAna.getId()).orElseThrow().getOperario())
            .isNull();
        assertThat(vehiculoRepository.findById(tresDeAna.getId()).orElseThrow().getOperario())
            .as("el ultimo de la lista tambien tiene que quedar libre")
            .isNull();

        assertThat(vehiculoRepository.findById(deLuis.getId()).orElseThrow().getOperario())
            .as("el vehiculo de OTRO empleado no se toca")
            .isNotNull();
        assertThat(vehiculoRepository.findById(sinOperario.getId()).orElseThrow().getOperario())
            .isNull();

        assertThat(personaRepository.findById(ana.getId()).orElseThrow())
            .isInstanceOfSatisfying(Empleado.class,
                e -> assertThat(e.getFechaBaja()).isNotNull());
    }

    /** Sin vehículos asignados la baja funciona igual y no rompe. */
    @Test
    @Transactional
    void desactivar_sinVehiculos_daDeBajaIgual() {
        empleadoService.desactivar(luis.getId());

        assertThat(personaRepository.findById(luis.getId()).orElseThrow())
            .isInstanceOfSatisfying(Empleado.class,
                e -> assertThat(e.getFechaBaja()).isNotNull());
    }

    // ------------------------------------------------------------------

    private Empleado empleado(String documento, String username, String nombre) {
        Empleado e = new Empleado();
        e.setNombre(nombre);
        e.setApellido("Apellido");
        e.setDocumento(documento);
        e.setUsername(username);
        e.setPassword("x");
        e.setRol(Rol.EMPLEADO);
        e.setTelefono(new Telefono("11", "55501234"));
        e.setFechaAlta(LocalDate.now());
        return e;
    }

    private Vehiculo vehiculoGuardado(String identificador, Empleado operario) {
        Vehiculo v = new Vehiculo();
        v.setIdentificador(identificador);
        v.setModelo("Hilux");
        v.setTipoVehiculo(TipoVehiculo.CAMIONETA);
        v.setTipoCombustible(TipoCombustible.GASOIL_GRADO_2);
        v.setCapacidadTanque(80);
        v.setEstado(Estado.DISPONIBLE);
        v.setUsoAcumulado(1000);
        v.setConsumoPromedio(new BigDecimal("12.00"));
        v.setFechaUltimoMantenimiento(LocalDate.now());
        v.setOperario(operario);
        return vehiculoRepository.save(v);
    }
}
