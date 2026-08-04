package com.retrorental.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.retrorental.backend.dto.request.CreateTicketRequest;
import com.retrorental.backend.exception.ConflictException;
import com.retrorental.backend.exception.ErrorCode;
import com.retrorental.backend.model.Empleado;
import com.retrorental.backend.model.Precio;
import com.retrorental.backend.model.Proveedor;
import com.retrorental.backend.model.Ticket;
import com.retrorental.backend.model.Vehiculo;
import com.retrorental.backend.model.enums.Estado;
import com.retrorental.backend.model.enums.Rol;
import com.retrorental.backend.model.enums.Servicio;
import com.retrorental.backend.model.enums.TipoCombustible;
import com.retrorental.backend.model.enums.TipoVehiculo;
import com.retrorental.backend.repository.PersonaRepository;
import com.retrorental.backend.repository.PrecioRepository;
import com.retrorental.backend.repository.ProveedorRepository;
import com.retrorental.backend.repository.TicketRepository;
import com.retrorental.backend.repository.VehiculoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Reglas de negocio de la carga de tickets que no pasan por la capa web:
 * el freno al precio absurdo y el contador que no puede retroceder.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TicketServiceTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private PrecioRepository precioRepository;
    @Mock private ProveedorRepository proveedorRepository;
    @Mock private VehiculoRepository vehiculoRepository;
    @Mock private PersonaRepository personaRepository;
    @Mock private StorageService storageService;
    @Mock private ObjectProvider<TicketAnalysisService> analysisProvider;

    @InjectMocks private TicketService service;

    private Proveedor proveedor;
    private Precio vigente;
    private Vehiculo vehiculo;

    private static final BigDecimal PRECIO_VIGENTE = new BigDecimal("2086.00");

    @BeforeEach
    void setUp() {
        // El margen se inyecta con @Value; en un test unitario se setea a mano.
        ReflectionTestUtils.setField(service, "margenMaximo", new BigDecimal("30"));

        proveedor = new Proveedor();
        proveedor.setId(1);
        proveedor.setNombre("YPF En Ruta");
        proveedor.setServicio(Servicio.COMBUSTIBLE);

        vigente = new Precio();
        vigente.setId(10);
        vigente.setPrecioUnitario(PRECIO_VIGENTE);
        vigente.setServicio(Servicio.COMBUSTIBLE);
        vigente.setTipoCombustible(TipoCombustible.GASOIL_GRADO_2);
        vigente.setProveedor(proveedor);

        vehiculo = new Vehiculo();
        vehiculo.setId(5);
        vehiculo.setEstado(Estado.DISPONIBLE);
        vehiculo.setTipoVehiculo(TipoVehiculo.MAQUINA);
        vehiculo.setTipoCombustible(TipoCombustible.GASOIL_GRADO_2);
        vehiculo.setUsoAcumulado(1000);

        Empleado empleado = new Empleado();
        empleado.setId(2);
        empleado.setUsername("juanperez");
        empleado.setRol(Rol.EMPLEADO);

        when(personaRepository.findByUsername("juanperez")).thenReturn(Optional.of(empleado));
        when(precioRepository.findById(10)).thenReturn(Optional.of(vigente));
        when(proveedorRepository.findById(1)).thenReturn(Optional.of(proveedor));
        when(vehiculoRepository.findById(5)).thenReturn(Optional.of(vehiculo));
        when(precioRepository.findByProveedorAndTipoCombustibleAndFechaHastaIsNull(
            proveedor, TipoCombustible.GASOIL_GRADO_2)).thenReturn(Optional.of(vigente));
        when(precioRepository.save(any(Precio.class))).thenAnswer(i -> i.getArgument(0));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(i -> {
            Ticket t = i.getArgument(0);
            t.setId(99);
            return t;
        });
    }

    private CreateTicketRequest request(BigDecimal precioUnitario, Integer usoAcumulado) {
        CreateTicketRequest req = new CreateTicketRequest();
        req.setLitros(50.0);
        req.setIdPrecio(10);
        req.setIdProveedor(1);
        req.setIdVehiculo(5);
        req.setPrecioUnitario(precioUnitario);
        req.setUsoAcumulado(usoAcumulado);
        return req;
    }

    // ---------------------------------------------------------------- precio

    @Test
    void sinPrecioManual_usaElDelCatalogoYNoLoToca() {
        service.create(request(null, 1200), "juanperez");

        verify(precioRepository, never()).save(any(Precio.class));
        assertEquals(PRECIO_VIGENTE, vigente.getPrecioUnitario());
    }

    @Test
    void precioCorregidoDentroDelMargen_actualizaElCatalogo() {
        BigDecimal corregido = new BigDecimal("2106.00"); // +20, dentro de 30

        service.create(request(corregido, 1200), "juanperez");

        // El vigente anterior se retira en vez de borrarse: los tickets viejos
        // siguen apuntando al precio que realmente se pago.
        assertEquals(java.time.LocalDate.now(), vigente.getFechaHasta());

        ArgumentCaptor<Precio> captor = ArgumentCaptor.forClass(Precio.class);
        verify(precioRepository).save(captor.capture());
        Precio nuevo = captor.getValue();
        assertEquals(corregido, nuevo.getPrecioUnitario());
        assertEquals(proveedor, nuevo.getProveedor());
        assertEquals(TipoCombustible.GASOIL_GRADO_2, nuevo.getTipoCombustible());
    }

    @Test
    void precioCorregidoFueraDelMargen_rechazaYNoTocaElCatalogo() {
        BigDecimal absurdo = new BigDecimal("20860.00"); // un cero de mas

        ConflictException ex = assertThrows(ConflictException.class,
            () -> service.create(request(absurdo, 1200), "juanperez"));

        assertEquals(ErrorCode.PRECIO_FUERA_DE_RANGO, ex.getCode());
        verify(precioRepository, never()).save(any(Precio.class));
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void precioEnElBordeDelMargen_seAcepta() {
        BigDecimal borde = PRECIO_VIGENTE.add(new BigDecimal("30")); // exactamente 30

        service.create(request(borde, 1200), "juanperez");

        verify(precioRepository).save(any(Precio.class));
    }

    // -------------------------------------------------------------- contador

    @Test
    void lecturaMayor_actualizaElContadorDelVehiculo() {
        service.create(request(null, 1250), "juanperez");

        assertEquals(1250, vehiculo.getUsoAcumulado());
        verify(vehiculoRepository).save(vehiculo);
    }

    @Test
    void lecturaMenorQueLaAnterior_rechaza() {
        ConflictException ex = assertThrows(ConflictException.class,
            () -> service.create(request(null, 900), "juanperez"));

        assertEquals(ErrorCode.USO_ACUMULADO_RETROCEDE, ex.getCode());
        assertEquals(1000, vehiculo.getUsoAcumulado());
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void lecturaIgualALaAnterior_seAcepta() {
        // Cargar combustible sin haber movido la maquina es plausible.
        service.create(request(null, 1000), "juanperez");

        verify(ticketRepository).save(any(Ticket.class));
    }

    @Test
    void laLecturaQuedaGuardadaEnElTicket() {
        service.create(request(null, 1300), "juanperez");

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        assertEquals(1300, captor.getValue().getUsoAcumulado());
    }
}
