package com.retrorental.backend.service;

import com.retrorental.backend.TestcontainersConfiguration;
import com.retrorental.backend.dto.response.TicketResponse;
import com.retrorental.backend.model.Empleado;
import com.retrorental.backend.model.Herramienta;
import com.retrorental.backend.model.Precio;
import com.retrorental.backend.model.Proveedor;
import com.retrorental.backend.model.Ticket;
import com.retrorental.backend.model.Vehiculo;
import com.retrorental.backend.model.embeddable.Telefono;
import com.retrorental.backend.model.enums.Estado;
import com.retrorental.backend.model.enums.Rol;
import com.retrorental.backend.model.enums.Servicio;
import com.retrorental.backend.model.enums.TipoCombustible;
import com.retrorental.backend.model.enums.TipoVehiculo;
import com.retrorental.backend.repository.HerramientaRepository;
import com.retrorental.backend.repository.PersonaRepository;
import com.retrorental.backend.repository.PrecioRepository;
import com.retrorental.backend.repository.ProveedorRepository;
import com.retrorental.backend.repository.TicketRepository;
import com.retrorental.backend.repository.VehiculoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de la capa de datos de tickets contra un Postgres REAL (Testcontainers).
 *
 * Por que no alcanzan los @WebMvcTest que ya existen: esos mockean el service,
 * asi que el SQL que Hibernate genera nunca se ejecuta. Los dos bugs que este
 * archivo vigila son invisibles para un test con el service mockeado, porque
 * viven exactamente en ese SQL:
 *
 * 1. El JoinType de los fetch joins de listForAdmin. Un INNER JOIN sobre
 *    vehiculo hace DESAPARECER del listado los tickets de herramienta (que
 *    tienen id_vehiculo NULL), sin error ni warning: simplemente devuelve menos
 *    filas. Es perdida de datos silenciosa en el panel del admin.
 *
 * 2. Que los fetch joins convivan con la paginacion. Si alguien agrega un fetch
 *    de una COLECCION, Hibernate deja de paginar en SQL y pagina en memoria
 *    (HHH000104): sigue "andando" pero se trae la tabla entera.
 *
 * Requiere Docker corriendo, como BackendApplicationTests.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Tag("ticket")
class TicketPersistenciaTest {

    @Autowired private TicketService ticketService;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private PersonaRepository personaRepository;
    @Autowired private ProveedorRepository proveedorRepository;
    @Autowired private PrecioRepository precioRepository;
    @Autowired private VehiculoRepository vehiculoRepository;
    @Autowired private HerramientaRepository herramientaRepository;

    // toResponse pide URLs presignadas; sin MinIO levantado el mock alcanza y
    // mantiene el test enfocado en el SQL.
    @MockitoBean private MinioStorageService storageService;

    private Empleado empleado;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        herramientaRepository.deleteAll();
        vehiculoRepository.deleteAll();
        precioRepository.deleteAll();
        proveedorRepository.deleteAll();
        personaRepository.deleteAll();

        empleado = new Empleado();
        empleado.setNombre("Ana");
        empleado.setApellido("Gomez");
        empleado.setDocumento("30111222");
        empleado.setUsername("agomez");
        empleado.setPassword("x");
        empleado.setRol(Rol.EMPLEADO);
        empleado.setTelefono(new Telefono("11", "55501234"));
        empleado.setFechaAlta(LocalDate.now());
        empleado = personaRepository.save(empleado);
    }

    /**
     * REGRESION: el listado del admin tiene que incluir los tickets de
     * herramienta.
     *
     * Con root.fetch("vehiculo") sin JoinType (INNER, el default de la Criteria
     * API) este test falla devolviendo solo el ticket de vehiculo: el de
     * herramienta se pierde.
     */
    @Test
    void listForAdmin_incluyeLosTicketsDeHerramientaYNoSoloLosDeVehiculo() {
        Proveedor proveedor = proveedorGuardado();
        Precio precio = precioGuardado(proveedor);
        crearTicketDeVehiculo(proveedor, precio);
        crearTicketDeHerramienta(proveedor, precio);

        PagedModel<TicketResponse> pagina = ticketService.listForAdmin(
            null, null, null, null, null, null, null, false,
            PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "fechaCarga")));

        List<TicketResponse> tickets = List.copyOf(pagina.getContent());

        assertThat(tickets)
            .as("el admin tiene que ver AMBOS origenes de carga")
            .hasSize(2);
        assertThat(tickets).anyMatch(t -> t.idVehiculo() != null && t.idHerramienta() == null);
        assertThat(tickets).anyMatch(t -> t.idHerramienta() != null && t.idVehiculo() == null);
    }

    /**
     * El historial del empleado tambien tiene que mostrar los dos origenes, y
     * respetar el tamanio de pagina.
     */
    @Test
    void listMine_paginaYMuestraLosDosOrigenesDeCarga() {
        Proveedor proveedor = proveedorGuardado();
        Precio precio = precioGuardado(proveedor);
        crearTicketDeVehiculo(proveedor, precio);
        crearTicketDeHerramienta(proveedor, precio);

        PagedModel<TicketResponse> primeraPagina = ticketService.listMine(
            empleado.getUsername(),
            PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "fechaCarga")));

        assertThat(primeraPagina.getContent())
            .as("size=1 tiene que devolver UNA fila, no el historial entero")
            .hasSize(1);
        assertThat(primeraPagina.getMetadata()).isNotNull();
        assertThat(primeraPagina.getMetadata().totalElements()).isEqualTo(2);

        PagedModel<TicketResponse> todo = ticketService.listMine(
            empleado.getUsername(),
            PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "fechaCarga")));

        assertThat(List.copyOf(todo.getContent()))
            .as("sin filtro de tamanio, los dos origenes")
            .hasSize(2);
    }

    /** Un ticket anulado no aparece en el historial del empleado. */
    @Test
    void listMine_noDevuelveLosAnulados() {
        Proveedor proveedor = proveedorGuardado();
        Precio precio = precioGuardado(proveedor);
        Ticket vigente = crearTicketDeVehiculo(proveedor, precio);
        Ticket anulado = crearTicketDeHerramienta(proveedor, precio);
        anulado.setFechaAnulacion(LocalDateTime.now());
        ticketRepository.save(anulado);

        PagedModel<TicketResponse> pagina = ticketService.listMine(
            empleado.getUsername(),
            PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "fechaCarga")));

        assertThat(List.copyOf(pagina.getContent()))
            .extracting(TicketResponse::id)
            .containsExactly(vigente.getId());
    }

    // ------------------------------------------------------------------
    // findForStats: los filtros opcionales viven en el SQL, no en memoria
    // ------------------------------------------------------------------

    /**
     * Los filtros de `findForStats` se movieron del servicio a la consulta (ver
     * docs/BACKEND-AUDIT.md, SVC-02), así que hay que probarlos contra una base
     * real: un test con el repositorio mockeado no ejerce el SQL.
     */
    @Test
    void findForStats_sinFiltros_traeLosDosOrigenesDeCarga() {
        Proveedor proveedor = proveedorGuardado();
        Precio precio = precioGuardado(proveedor);
        crearTicketDeVehiculo(proveedor, precio);
        crearTicketDeHerramienta(proveedor, precio);

        List<Ticket> encontrados = ticketRepository.findForStats(
            LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
            null, false, List.of(-1));

        assertThat(encontrados).hasSize(2);
    }

    /**
     * Filtrar por vehículo excluye de por sí los tickets de herramienta, porque
     * esos tienen id_vehiculo en NULL. Es la propiedad en la que se apoyaba el
     * filtrado en memoria que se eliminó.
     */
    @Test
    void findForStats_filtradoPorVehiculo_dejaAfueraLasCargasDeHerramienta() {
        Proveedor proveedor = proveedorGuardado();
        Precio precio = precioGuardado(proveedor);
        Ticket delVehiculo = crearTicketDeVehiculo(proveedor, precio);
        crearTicketDeHerramienta(proveedor, precio);

        List<Ticket> encontrados = ticketRepository.findForStats(
            LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
            delVehiculo.getVehiculo().getId(), false, List.of(-1));

        assertThat(encontrados)
            .extracting(Ticket::getId)
            .containsExactly(delVehiculo.getId());
    }

    @Test
    void findForStats_filtradoPorEmpleado_soloTraeLosDeEseEmpleado() {
        Proveedor proveedor = proveedorGuardado();
        Precio precio = precioGuardado(proveedor);
        crearTicketDeVehiculo(proveedor, precio);
        crearTicketDeHerramienta(proveedor, precio);

        // Con el flag encendido y un id que no existe, no tiene que traer nada.
        assertThat(ticketRepository.findForStats(
            LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
            null, true, List.of(-999)))
            .isEmpty();

        // Con el id real, los dos tickets del empleado.
        assertThat(ticketRepository.findForStats(
            LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
            null, true, List.of(empleado.getId())))
            .hasSize(2);
    }

    /** Un ticket anulado nunca entra en las estadísticas. */
    @Test
    void findForStats_ignoraLosAnulados() {
        Proveedor proveedor = proveedorGuardado();
        Precio precio = precioGuardado(proveedor);
        Ticket vigente = crearTicketDeVehiculo(proveedor, precio);
        Ticket anulado = crearTicketDeHerramienta(proveedor, precio);
        anulado.setFechaAnulacion(LocalDateTime.now());
        ticketRepository.save(anulado);

        assertThat(ticketRepository.findForStats(
            LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
            null, false, List.of(-1)))
            .extracting(Ticket::getId)
            .containsExactly(vigente.getId());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Proveedor proveedorGuardado() {
        Proveedor proveedor = new Proveedor();
        proveedor.setNombre("YPF Centro");
        proveedor.setCuit("20123456781");
        proveedor.setServicio(Servicio.COMBUSTIBLE);
        return proveedorRepository.save(proveedor);
    }

    private Precio precioGuardado(Proveedor proveedor) {
        Precio precio = new Precio();
        precio.setPrecioUnitario(new BigDecimal("1200.00"));
        precio.setTipoCombustible(TipoCombustible.GASOIL_GRADO_2);
        precio.setServicio(Servicio.COMBUSTIBLE);
        precio.setProveedor(proveedor);
        precio.setFechaDesde(LocalDate.now());
        return precioRepository.save(precio);
    }

    private Ticket crearTicketDeVehiculo(Proveedor proveedor, Precio precio) {
        Vehiculo vehiculo = new Vehiculo();
        vehiculo.setIdentificador("AA123BB");
        vehiculo.setModelo("Hilux");
        vehiculo.setTipoVehiculo(TipoVehiculo.CAMIONETA);
        vehiculo.setTipoCombustible(TipoCombustible.GASOIL_GRADO_2);
        vehiculo.setCapacidadTanque(80);
        vehiculo.setEstado(Estado.DISPONIBLE);
        vehiculo.setUsoAcumulado(1000);
        vehiculo.setConsumoPromedio(new BigDecimal("12.00"));
        vehiculo.setFechaUltimoMantenimiento(LocalDate.now());
        vehiculo = vehiculoRepository.save(vehiculo);

        Ticket ticket = new Ticket();
        ticket.setLitros(50.0);
        ticket.setFechaCarga(LocalDateTime.now().minusHours(1));
        ticket.setUsoAcumulado(1100);
        ticket.setPrecio(precio);
        ticket.setProveedor(proveedor);
        ticket.setPersona(empleado);
        ticket.setVehiculo(vehiculo);
        return ticketRepository.save(ticket);
    }

    private Ticket crearTicketDeHerramienta(Proveedor proveedor, Precio precio) {
        Herramienta herramienta = new Herramienta();
        herramienta.setNombre("Motosierra");
        herramienta.setCapacidad(new BigDecimal("5.00"));
        herramienta = herramientaRepository.save(herramienta);

        Ticket ticket = new Ticket();
        ticket.setLitros(3.5);
        ticket.setFechaCarga(LocalDateTime.now());
        ticket.setPrecio(precio);
        ticket.setProveedor(proveedor);
        ticket.setPersona(empleado);
        ticket.setHerramienta(herramienta);
        return ticketRepository.save(ticket);
    }
}
