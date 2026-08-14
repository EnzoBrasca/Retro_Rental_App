package com.retrorental.backend.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.retrorental.backend.dto.response.StatsResponse;
import com.retrorental.backend.model.Empleado;
import com.retrorental.backend.model.Herramienta;
import com.retrorental.backend.model.Persona;
import com.retrorental.backend.model.Precio;
import com.retrorental.backend.model.Proveedor;
import com.retrorental.backend.model.Ticket;
import com.retrorental.backend.model.Vehiculo;
import com.retrorental.backend.model.enums.Rol;
import com.retrorental.backend.model.enums.Servicio;
import com.retrorental.backend.model.enums.TipoCombustible;
import com.retrorental.backend.model.enums.TipoVehiculo;
import com.retrorental.backend.model.enums.UnidadUso;
import com.retrorental.backend.repository.TicketRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * StatsService debe seguir funcionando cuando aparecen tickets de HERRAMIENTA
 * (ticket.getVehiculo() == null). Antes de esta feature, findForStats hacia un
 * INNER JOIN FETCH sobre vehiculo, y statsForRange llamaba
 * t.getVehiculo().getId() sin chequeo de null: los dos hubieran roto (uno
 * excluyendo silenciosamente los tickets de herramienta de las estadisticas, el
 * otro con NullPointerException) apenas existiera un ticket sin vehiculo.
 */
@ExtendWith(MockitoExtension.class)
@Tag("stats")
class StatsServiceTest {

    @Mock private TicketRepository ticketRepository;

    @InjectMocks private StatsService service;

    private Ticket ticketDeVehiculo(int idVehiculo, double litros, BigDecimal precioUnitario) {
        Vehiculo vehiculo = new Vehiculo();
        vehiculo.setId(idVehiculo);
        vehiculo.setTipoVehiculo(TipoVehiculo.CAMION);

        return ticket(litros, precioUnitario, vehiculo, null);
    }

    /**
     * Carga de un vehiculo tal como la devuelve la consulta de consumo: con
     * lectura del contador y fecha, que es lo que define si cae dentro del rango
     * o si es la linea base anterior a el.
     */
    private Ticket carga(TipoVehiculo tipo, int uso, double litros, LocalDateTime fechaCarga) {
        Vehiculo vehiculo = new Vehiculo();
        vehiculo.setId(1);
        vehiculo.setTipoVehiculo(tipo);

        Ticket t = ticket(litros, new BigDecimal("2000"), vehiculo, null);
        t.setUsoAcumulado(uso);
        t.setFechaCarga(fechaCarga);
        return t;
    }

    private void cargasDelVehiculo(Ticket... cargas) {
        when(ticketRepository
            .findByVehiculoIdAndUsoAcumuladoIsNotNullAndFechaAnulacionIsNullOrderByUsoAcumuladoAsc(1))
            .thenReturn(List.of(cargas));
    }

    private Ticket ticketDeHerramienta(int idHerramienta, double litros, BigDecimal precioUnitario) {
        Herramienta herramienta = new Herramienta();
        herramienta.setId(idHerramienta);
        herramienta.setNombre("Motosierra");
        herramienta.setCapacidad(new BigDecimal("0.30"));

        return ticket(litros, precioUnitario, null, herramienta);
    }

    private Ticket ticket(double litros, BigDecimal precioUnitario, Vehiculo vehiculo, Herramienta herramienta) {
        Proveedor proveedor = new Proveedor();
        proveedor.setId(1);
        proveedor.setNombre("YPF");
        proveedor.setServicio(Servicio.COMBUSTIBLE);

        Precio precio = new Precio();
        precio.setId(1);
        precio.setPrecioUnitario(precioUnitario);
        precio.setServicio(Servicio.COMBUSTIBLE);
        precio.setTipoCombustible(TipoCombustible.NAFTA_SUPER);

        Empleado empleado = new Empleado();
        empleado.setId(2);
        empleado.setNombre("Juan");
        empleado.setApellido("Perez");
        empleado.setUsername("juanperez");
        empleado.setRol(Rol.EMPLEADO);

        Ticket t = new Ticket();
        t.setLitros(litros);
        t.setPrecio(precio);
        t.setProveedor(proveedor);
        t.setPersona(empleado);
        t.setVehiculo(vehiculo);
        t.setHerramienta(herramienta);
        return t;
    }

    @Test
    void statsConTicketsDeHerramienta_noRompeConNullPointer() {
        when(ticketRepository.findForStats(any(), any())).thenReturn(List.of(
            ticketDeVehiculo(1, 50.0, new BigDecimal("2000")),
            ticketDeHerramienta(7, 0.3, new BigDecimal("2100"))
        ));

        assertDoesNotThrow(() -> service.daily(LocalDate.of(2026, 8, 1), null, null));
    }

    @Test
    void statsConTicketsDeHerramienta_sumaElGastoDeAmbosOrigenes() {
        when(ticketRepository.findForStats(any(), any())).thenReturn(List.of(
            ticketDeVehiculo(1, 50.0, new BigDecimal("2000")),
            ticketDeHerramienta(7, 0.3, new BigDecimal("2100"))
        ));

        StatsResponse resp = service.daily(LocalDate.of(2026, 8, 1), null, null);

        // El gasto total incluye la carga de la herramienta: 50*2000 + 0.3*2100
        assertEquals(new BigDecimal("100630.00"), resp.gastoTotal());
        assertEquals(2, resp.cantidadRegistros());
    }

    @Test
    void vehiculosActivos_noCuentaLosTicketsDeHerramienta() {
        when(ticketRepository.findForStats(any(), any())).thenReturn(List.of(
            ticketDeVehiculo(1, 50.0, new BigDecimal("2000")),
            ticketDeHerramienta(7, 0.3, new BigDecimal("2100"))
        ));

        StatsResponse resp = service.daily(LocalDate.of(2026, 8, 1), null, null);

        // Solo el vehiculo 1 cuenta: la herramienta no es un vehiculo.
        assertEquals(1, resp.vehiculosActivos());
    }

    @Test
    void promedioLitrosPorVehiculo_noIncluyeLosLitrosDeHerramienta() {
        when(ticketRepository.findForStats(any(), any())).thenReturn(List.of(
            ticketDeVehiculo(1, 50.0, new BigDecimal("2000")),
            ticketDeHerramienta(7, 0.3, new BigDecimal("2100"))
        ));

        StatsResponse resp = service.daily(LocalDate.of(2026, 8, 1), null, null);

        // El promedio es POR VEHICULO: su denominador cuenta solo vehiculos, asi
        // que el numerador tampoco puede incluir los litros de la herramienta.
        // Con un unico vehiculo que cargo 50 L, el promedio es 50, no 50.3.
        assertEquals(50.0, resp.promedioLitrosPorVehiculo());
    }

    @Test
    void filtroPorVehiculoId_excluyeLosTicketsDeHerramienta() {
        when(ticketRepository.findForStats(any(), any())).thenReturn(List.of(
            ticketDeVehiculo(1, 50.0, new BigDecimal("2000")),
            ticketDeHerramienta(7, 0.3, new BigDecimal("2100"))
        ));

        StatsResponse resp = service.daily(LocalDate.of(2026, 8, 1), 1, null);

        assertEquals(1, resp.cantidadRegistros());
    }

    // -----------------------------------------------------------------------
    // Consumo del vehiculo seleccionado.
    //
    // Es el UNICO dato del panel que no sale de agregar los tickets del rango:
    // el metodo de tanque lleno necesita la carga ANTERIOR al rango como linea
    // base, porque los litros de una carga reponen lo gastado desde la previa.
    // -----------------------------------------------------------------------

    @Test
    void consumo_esNullSinVehiculoSeleccionado() {
        when(ticketRepository.findForStats(any(), any())).thenReturn(List.of(
            ticketDeVehiculo(1, 50.0, new BigDecimal("2000"))
        ));

        StatsResponse resp = service.daily(LocalDate.of(2026, 8, 5), null, null);

        // Sin vehiculo seleccionado el promedio de la flota mezclaria maquinas
        // (L/h) con camiones (L/100km): son unidades distintas, no se promedian.
        assertEquals(null, resp.consumoPeriodo());
        assertEquals(null, resp.unidadUso());
    }

    @Test
    void consumo_deMaquinaSeExpresaEnLitrosPorHora() {
        when(ticketRepository.findForStats(any(), any())).thenReturn(List.of());
        cargasDelVehiculo(
            carga(TipoVehiculo.MAQUINA, 1000, 40.0, LocalDateTime.of(2026, 8, 1, 9, 0)),
            carga(TipoVehiculo.MAQUINA, 1010, 30.0, LocalDateTime.of(2026, 8, 5, 10, 0))
        );

        StatsResponse resp = service.daily(LocalDate.of(2026, 8, 5), 1, null);

        // 30 L en las 10 h que van de 1000 a 1010. Los 40 L de la linea base no
        // cuentan: esa carga marca el inicio del tramo, no consumo dentro de el.
        assertEquals(new BigDecimal("3.00"), resp.consumoPeriodo());
        assertEquals(UnidadUso.HORAS, resp.unidadUso());
    }

    @Test
    void consumo_deCamionSeExpresaCada100Km() {
        when(ticketRepository.findForStats(any(), any())).thenReturn(List.of());
        cargasDelVehiculo(
            carga(TipoVehiculo.CAMION, 5000, 40.0, LocalDateTime.of(2026, 8, 1, 9, 0)),
            carga(TipoVehiculo.CAMION, 5200, 30.0, LocalDateTime.of(2026, 8, 5, 10, 0))
        );

        StatsResponse resp = service.daily(LocalDate.of(2026, 8, 5), 1, null);

        // 30 L en 200 km => 15 L cada 100 km.
        assertEquals(new BigDecimal("15.00"), resp.consumoPeriodo());
        assertEquals(UnidadUso.KM, resp.unidadUso());
    }

    @Test
    void consumo_ignoraLasCargasPosterioresAlRango() {
        when(ticketRepository.findForStats(any(), any())).thenReturn(List.of());
        cargasDelVehiculo(
            carga(TipoVehiculo.MAQUINA, 1000, 40.0, LocalDateTime.of(2026, 8, 1, 9, 0)),
            carga(TipoVehiculo.MAQUINA, 1010, 30.0, LocalDateTime.of(2026, 8, 5, 10, 0)),
            // Ya fuera del dia consultado: no puede entrar al calculo, o el dato
            // dejaria de corresponder al periodo que el admin tiene en pantalla.
            carga(TipoVehiculo.MAQUINA, 1040, 90.0, LocalDateTime.of(2026, 8, 9, 10, 0))
        );

        StatsResponse resp = service.daily(LocalDate.of(2026, 8, 5), 1, null);

        assertEquals(new BigDecimal("3.00"), resp.consumoPeriodo());
    }

    @Test
    void consumo_conDosCargasDentroDelRangoNoNecesitaLineaBasePrevia() {
        when(ticketRepository.findForStats(any(), any())).thenReturn(List.of());
        cargasDelVehiculo(
            carga(TipoVehiculo.MAQUINA, 1000, 40.0, LocalDateTime.of(2026, 8, 5, 8, 0)),
            carga(TipoVehiculo.MAQUINA, 1020, 50.0, LocalDateTime.of(2026, 8, 5, 18, 0))
        );

        StatsResponse resp = service.daily(LocalDate.of(2026, 8, 5), 1, null);

        // La primera del rango oficia de linea base: 50 L en 20 h.
        assertEquals(new BigDecimal("2.50"), resp.consumoPeriodo());
    }

    @Test
    void consumo_esNullConUnaSolaCargaYSinLineaBase() {
        when(ticketRepository.findForStats(any(), any())).thenReturn(List.of());
        cargasDelVehiculo(
            carga(TipoVehiculo.MAQUINA, 1000, 40.0, LocalDateTime.of(2026, 8, 5, 10, 0))
        );

        StatsResponse resp = service.daily(LocalDate.of(2026, 8, 5), 1, null);

        // Una sola carga no define ningun intervalo: no hay consumo que informar.
        // Devolver 0 seria peor que no devolver nada, porque se leeria como dato.
        assertEquals(null, resp.consumoPeriodo());
    }

    @Test
    void consumo_ignoraElFiltroDeEmpleado() {
        when(ticketRepository.findForStats(any(), any())).thenReturn(List.of());
        cargasDelVehiculo(
            carga(TipoVehiculo.MAQUINA, 1000, 40.0, LocalDateTime.of(2026, 8, 1, 9, 0)),
            carga(TipoVehiculo.MAQUINA, 1010, 30.0, LocalDateTime.of(2026, 8, 5, 10, 0))
        );

        StatsResponse resp = service.daily(LocalDate.of(2026, 8, 5), 1, List.of(999));

        // Decision explicita: el consumo se calcula sobre TODAS las cargas del
        // vehiculo. Sacar del medio las cargas de otro empleado dejaria el uso
        // completo del intervalo sin los litros que lo repusieron, y el numero
        // saldria sistematicamente bajo. El consumo es del vehiculo, no de quien
        // apreto el surtidor.
        assertEquals(new BigDecimal("3.00"), resp.consumoPeriodo());
    }
}
