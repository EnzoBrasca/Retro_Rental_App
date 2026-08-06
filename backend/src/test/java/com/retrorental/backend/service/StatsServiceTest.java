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
import com.retrorental.backend.repository.TicketRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
class StatsServiceTest {

    @Mock private TicketRepository ticketRepository;

    @InjectMocks private StatsService service;

    private Ticket ticketDeVehiculo(int idVehiculo, double litros, BigDecimal precioUnitario) {
        Vehiculo vehiculo = new Vehiculo();
        vehiculo.setId(idVehiculo);
        vehiculo.setTipoVehiculo(TipoVehiculo.CAMION);

        return ticket(litros, precioUnitario, vehiculo, null);
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
}
