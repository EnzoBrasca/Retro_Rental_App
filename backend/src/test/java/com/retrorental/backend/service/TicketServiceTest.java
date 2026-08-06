package com.retrorental.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.retrorental.backend.dto.request.CreateTicketRequest;
import com.retrorental.backend.exception.ConflictException;
import com.retrorental.backend.exception.AppException;
import com.retrorental.backend.exception.ErrorCode;
import com.retrorental.backend.exception.ResourceNotFoundException;
import com.retrorental.backend.model.Administrador;
import java.time.LocalDateTime;
import java.util.List;
import com.retrorental.backend.model.Empleado;
import com.retrorental.backend.model.Herramienta;
import com.retrorental.backend.model.Precio;
import com.retrorental.backend.model.Proveedor;
import com.retrorental.backend.model.Ticket;
import com.retrorental.backend.model.Vehiculo;
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
    @Mock private HerramientaRepository herramientaRepository;
    @Mock private PersonaRepository personaRepository;
    @Mock private StorageService storageService;
    @Mock private ObjectProvider<TicketAnalysisService> analysisProvider;

    @InjectMocks private TicketService service;

    private Proveedor proveedor;
    private Precio vigente;
    private Vehiculo vehiculo;
    private Herramienta herramienta;

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

        herramienta = new Herramienta();
        herramienta.setId(7);
        herramienta.setNombre("Motosierra Stihl");
        herramienta.setCapacidad(new BigDecimal("0.30"));

        Empleado empleado = new Empleado();
        empleado.setId(2);
        empleado.setUsername("juanperez");
        empleado.setRol(Rol.EMPLEADO);

        when(personaRepository.findByUsername("juanperez")).thenReturn(Optional.of(empleado));
        when(precioRepository.findById(10)).thenReturn(Optional.of(vigente));
        when(proveedorRepository.findById(1)).thenReturn(Optional.of(proveedor));
        when(vehiculoRepository.findById(5)).thenReturn(Optional.of(vehiculo));
        when(herramientaRepository.findById(7)).thenReturn(Optional.of(herramienta));
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

    private CreateTicketRequest requestHerramienta(BigDecimal precioUnitario) {
        CreateTicketRequest req = new CreateTicketRequest();
        req.setLitros(0.3);
        req.setIdPrecio(10);
        req.setIdProveedor(1);
        req.setIdHerramienta(7);
        req.setPrecioUnitario(precioUnitario);
        return req;
    }

    // Contrato nuevo: una herramienta NO manda idPrecio, manda tipoCombustible
    // y el service resuelve el precio (ver resolvePrecioPorCombustible).
    private CreateTicketRequest requestHerramientaConCombustible(
            TipoCombustible tipoCombustible, BigDecimal precioUnitario) {
        CreateTicketRequest req = new CreateTicketRequest();
        req.setLitros(0.3);
        req.setTipoCombustible(tipoCombustible);
        req.setIdProveedor(1);
        req.setIdHerramienta(7);
        req.setPrecioUnitario(precioUnitario);
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

    @Test
    void precioDeMezcla_muyLejosDelVigente_NoAplicaElMargen() {
        // La mezcla lleva aceite: su precio real se aleja legitimamente del de
        // la nafta base que tomo como referencia. El margen de +-30 NO debe
        // aplicarle, o trabaria correcciones validas.
        vigente.setTipoCombustible(TipoCombustible.MEZCLA);
        BigDecimal muyLejos = PRECIO_VIGENTE.add(new BigDecimal("500")); // muy fuera de +-30

        service.create(request(muyLejos, 1200), "juanperez");

        verify(precioRepository).save(any(Precio.class));
        verify(ticketRepository).save(any(Ticket.class));
    }

    // --------------------------------------------------- precio por combustible

    @Test
    void herramientaConMezcla_sinPrecioPrevio_copiaElDeNaftaSuperDelProveedor() {
        // El proveedor no tiene (YPF, MEZCLA) todavia, pero si tiene (YPF,
        // NAFTA_SUPER): el service tiene que crear la mezcla copiando ese valor.
        when(precioRepository.findByProveedorAndTipoCombustibleAndFechaHastaIsNull(
            proveedor, TipoCombustible.MEZCLA)).thenReturn(Optional.empty());
        Precio naftaSuper = new Precio();
        naftaSuper.setId(20);
        naftaSuper.setPrecioUnitario(PRECIO_VIGENTE);
        naftaSuper.setServicio(Servicio.COMBUSTIBLE);
        naftaSuper.setTipoCombustible(TipoCombustible.NAFTA_SUPER);
        naftaSuper.setProveedor(proveedor);
        when(precioRepository.findByProveedorAndTipoCombustibleAndFechaHastaIsNull(
            proveedor, TipoCombustible.NAFTA_SUPER)).thenReturn(Optional.of(naftaSuper));

        service.create(requestHerramientaConCombustible(TipoCombustible.MEZCLA, null), "juanperez");

        ArgumentCaptor<Precio> captor = ArgumentCaptor.forClass(Precio.class);
        verify(precioRepository).save(captor.capture());
        Precio creado = captor.getValue();
        assertEquals(TipoCombustible.MEZCLA, creado.getTipoCombustible());
        assertEquals(proveedor, creado.getProveedor());
        assertEquals(PRECIO_VIGENTE, creado.getPrecioUnitario());
        // El de nafta super no se toca ni se cierra: solo sirvio de referencia.
        assertNull(naftaSuper.getFechaHasta());

        ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(ticketCaptor.capture());
        assertEquals(creado, ticketCaptor.getValue().getPrecio());
    }

    @Test
    void herramientaConMezcla_conPrecioPrevio_usaElSuyoYNoLoPisaConElDeNafta() {
        Precio mezclaVigente = new Precio();
        mezclaVigente.setId(30);
        mezclaVigente.setPrecioUnitario(new BigDecimal("2450.00"));
        mezclaVigente.setServicio(Servicio.COMBUSTIBLE);
        mezclaVigente.setTipoCombustible(TipoCombustible.MEZCLA);
        mezclaVigente.setProveedor(proveedor);
        when(precioRepository.findByProveedorAndTipoCombustibleAndFechaHastaIsNull(
            proveedor, TipoCombustible.MEZCLA)).thenReturn(Optional.of(mezclaVigente));

        service.create(requestHerramientaConCombustible(TipoCombustible.MEZCLA, null), "juanperez");

        // No se crea ni se toca ningun precio: ya habia uno vigente.
        verify(precioRepository, never()).save(any(Precio.class));
        verify(precioRepository, never()).findByProveedorAndTipoCombustibleAndFechaHastaIsNull(
            proveedor, TipoCombustible.NAFTA_SUPER);

        ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(ticketCaptor.capture());
        assertEquals(mezclaVigente, ticketCaptor.getValue().getPrecio());
    }

    @Test
    void herramientaConMezcla_sinNaftaSuperDelProveedor_rechazaConErrorClaro() {
        when(precioRepository.findByProveedorAndTipoCombustibleAndFechaHastaIsNull(
            proveedor, TipoCombustible.MEZCLA)).thenReturn(Optional.empty());
        when(precioRepository.findByProveedorAndTipoCombustibleAndFechaHastaIsNull(
            proveedor, TipoCombustible.NAFTA_SUPER)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
            () -> service.create(requestHerramientaConCombustible(TipoCombustible.MEZCLA, null), "juanperez"));

        assertEquals(ErrorCode.PRECIO_BASE_MEZCLA_NOT_FOUND, ex.getCode());
        verify(precioRepository, never()).save(any(Precio.class));
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void herramientaSinMezcla_sinPrecioVigenteEnElProveedor_rechazaSinInventarPrecio() {
        // Un bidon con GASOIL en un proveedor que no tiene ese precio cargado:
        // a diferencia de MEZCLA, aca no hay de donde copiar, asi que se rechaza.
        when(precioRepository.findByProveedorAndTipoCombustibleAndFechaHastaIsNull(
            proveedor, TipoCombustible.GASOIL_GRADO_2)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
            () -> service.create(
                requestHerramientaConCombustible(TipoCombustible.GASOIL_GRADO_2, null), "juanperez"));

        assertEquals(ErrorCode.PRECIO_NOT_FOUND_PARA_COMBUSTIBLE, ex.getCode());
        verify(precioRepository, never()).save(any(Precio.class));
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void vehiculoConIdPrecio_siguenFuncionandoIgualQueAntes() {
        // La carga de vehiculo con idPrecio no cambia: sigue resolviendose por
        // id, sin pasar por resolvePrecioPorCombustible.
        service.create(request(null, 1200), "juanperez");

        ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(ticketCaptor.capture());
        assertEquals(vigente, ticketCaptor.getValue().getPrecio());
        verify(precioRepository, never()).findByProveedorAndTipoCombustibleAndFechaHastaIsNull(
            proveedor, TipoCombustible.NAFTA_SUPER);
    }

    // ----------------------------------------------------------- herramienta

    @Test
    void crearConHerramienta_noTocaVehiculoNiContador() {
        service.create(requestHerramienta(null), "juanperez");

        // Una herramienta no tiene contador ni pool de operario: no hay
        // vehiculo que actualizar ni consumo que recalcular.
        verify(vehiculoRepository, never()).save(any(Vehiculo.class));

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        Ticket guardado = captor.getValue();
        assertEquals(herramienta, guardado.getHerramienta());
        assertNull(guardado.getVehiculo());
        assertNull(guardado.getUsoAcumulado());
    }

    @Test
    void crearConHerramientaDadaDeBaja_rechaza() {
        herramienta.setFechaBaja(java.time.LocalDate.now());

        ConflictException ex = assertThrows(ConflictException.class,
            () -> service.create(requestHerramienta(null), "juanperez"));

        assertEquals(ErrorCode.HERRAMIENTA_ALREADY_INACTIVE, ex.getCode());
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void crearConHerramientaInexistente_devuelve404() {
        when(herramientaRepository.findById(99)).thenReturn(Optional.empty());
        CreateTicketRequest req = requestHerramienta(null);
        req.setIdHerramienta(99);

        ResourceNotFoundException ex = assertThrows(
            ResourceNotFoundException.class,
            () -> service.create(req, "juanperez"));

        assertEquals(ErrorCode.HERRAMIENTA_NOT_FOUND, ex.getCode());
    }

    @Test
    void anularTicketDeHerramienta_noTocaNingunVehiculo() {
        Administrador admin = admin();
        Ticket ticketHerramienta = new Ticket();
        ticketHerramienta.setId(50);
        ticketHerramienta.setHerramienta(herramienta);
        when(ticketRepository.findById(50)).thenReturn(Optional.of(ticketHerramienta));

        service.anular(50, "admin");

        assertNotNull(ticketHerramienta.getFechaAnulacion());
        assertEquals(admin, ticketHerramienta.getAnuladoPor());
        verify(vehiculoRepository, never()).save(any(Vehiculo.class));
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

    // -----------------------------------------------------------------------
    // Anulacion (V7).
    //
    // Anular no es marcar una fila: el alta del ticket habia adelantado la
    // lectura del vehiculo y recalculado su consumo, y las dos cosas tienen que
    // deshacerse. Estos tests cubren esa reversion, que es la razon de ser de
    // la funcion.
    // -----------------------------------------------------------------------

    private Ticket ticketDe(int id, int uso, double litros) {
        Ticket t = new Ticket();
        t.setId(id);
        t.setUsoAcumulado(uso);
        t.setLitros(litros);
        t.setVehiculo(vehiculo);
        return t;
    }

    /** Deja al repo devolviendo `vigentes` como las cargas que sobreviven. */
    private void cargasVigentes(Ticket... vigentes) {
        when(ticketRepository
            .findByVehiculoIdAndUsoAcumuladoIsNotNullAndFechaAnulacionIsNullOrderByUsoAcumuladoAsc(5))
            .thenReturn(List.of(vigentes));
    }

    private Administrador admin() {
        Administrador a = new Administrador();
        a.setId(1);
        a.setUsername("admin");
        a.setRol(Rol.ADMINISTRADOR);
        when(personaRepository.findByUsername("admin")).thenReturn(Optional.of(a));
        return a;
    }

    @Test
    void anular_marcaLaFilaYRegistraQuienLoHizo() {
        Administrador quien = admin();
        Ticket t = ticketDe(99, 1300, 50.0);
        when(ticketRepository.findById(99)).thenReturn(Optional.of(t));
        cargasVigentes();

        service.anular(99, "admin");

        // La fila NO se borra: es un registro contable.
        verify(ticketRepository, never()).delete(any(Ticket.class));
        assertNotNull(t.getFechaAnulacion());
        assertEquals(quien, t.getAnuladoPor());
    }

    @Test
    void anular_devuelveLaLecturaDelVehiculoALaMayorVigente() {
        admin();
        // El vehiculo quedo en 9999 por un tipeo. Al anular ese ticket, la
        // lectura tiene que volver a la carga real anterior (1200). Sin esto el
        // vehiculo queda inutilizable: toda carga futura chocaria contra 9999.
        vehiculo.setUsoAcumulado(9999);
        Ticket tipeoMal = ticketDe(99, 9999, 50.0);
        when(ticketRepository.findById(99)).thenReturn(Optional.of(tipeoMal));
        cargasVigentes(ticketDe(97, 1000, 40.0), ticketDe(98, 1200, 45.0));

        service.anular(99, "admin");

        assertEquals(1200, vehiculo.getUsoAcumulado());
    }

    @Test
    void anular_sinCargasVigentes_dejaLaLecturaComoEstaba() {
        admin();
        // La lectura previa a todos los tickets no se guarda en ningun lado: no
        // hay a que volver. Se deja lo que hay antes que inventar un numero.
        vehiculo.setUsoAcumulado(9999);
        when(ticketRepository.findById(99)).thenReturn(Optional.of(ticketDe(99, 9999, 50.0)));
        cargasVigentes();

        service.anular(99, "admin");

        assertEquals(9999, vehiculo.getUsoAcumulado());
    }

    @Test
    void anular_hastaQuedarSinDatos_devuelveElConsumoAlDelAlta() {
        admin();
        // ESTE es el test que justifica la columna consumo_inicial. Con menos de
        // dos cargas no hay consumo real que calcular; sin el respaldo, el
        // vehiculo se quedaria con un consumo derivado de la carga anulada.
        vehiculo.setConsumoInicial(new BigDecimal("8.50"));
        vehiculo.setConsumoPromedio(new BigDecimal("14.20"));
        when(ticketRepository.findById(99)).thenReturn(Optional.of(ticketDe(99, 1300, 50.0)));
        cargasVigentes(ticketDe(98, 1000, 40.0));

        service.anular(99, "admin");

        assertEquals(new BigDecimal("8.50"), vehiculo.getConsumoPromedio());
        assertNull(vehiculo.getConsumoReciente());
    }

    @Test
    void anular_sinConsumoInicial_noInventaUnValor() {
        admin();
        // Vehiculos anteriores a V7: su estimacion original ya se habia perdido.
        // No hay a que volver, asi que se deja el ultimo calculado.
        vehiculo.setConsumoInicial(null);
        vehiculo.setConsumoPromedio(new BigDecimal("14.20"));
        when(ticketRepository.findById(99)).thenReturn(Optional.of(ticketDe(99, 1300, 50.0)));
        cargasVigentes(ticketDe(98, 1000, 40.0));

        service.anular(99, "admin");

        assertEquals(new BigDecimal("14.20"), vehiculo.getConsumoPromedio());
    }

    @Test
    void anular_conCargasSuficientes_recalculaElConsumoSinLaAnulada() {
        admin();
        vehiculo.setConsumoInicial(new BigDecimal("8.50"));
        when(ticketRepository.findById(99)).thenReturn(Optional.of(ticketDe(99, 1400, 90.0)));
        // Quedan dos cargas: 200 horas de intervalo y 45 litros -> 0,225 L/h.
        cargasVigentes(ticketDe(97, 1000, 40.0), ticketDe(98, 1200, 45.0));

        service.anular(99, "admin");

        // Se calcula, no se cae al respaldo del alta.
        assertNotEquals(new BigDecimal("8.50"), vehiculo.getConsumoPromedio());
        assertNotNull(vehiculo.getConsumoPromedio());
    }

    @Test
    void anular_ticketYaAnulado_rechaza() {
        Ticket t = ticketDe(99, 1300, 50.0);
        t.setFechaAnulacion(LocalDateTime.now());
        when(ticketRepository.findById(99)).thenReturn(Optional.of(t));

        AppException ex = assertThrows(AppException.class, () -> service.anular(99, "admin"));
        assertEquals(ErrorCode.TICKET_ALREADY_ANULADO, ex.getCode());
        verify(vehiculoRepository, never()).save(any(Vehiculo.class));
    }
}
