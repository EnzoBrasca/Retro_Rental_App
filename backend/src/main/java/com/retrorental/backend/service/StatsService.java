package com.retrorental.backend.service;

import com.retrorental.backend.dto.response.EmpleadoConsumo;
import com.retrorental.backend.dto.response.ProveedorConsumo;
import com.retrorental.backend.dto.response.StatsResponse;
import com.retrorental.backend.model.Persona;
import com.retrorental.backend.model.Proveedor;
import com.retrorental.backend.model.Ticket;
import com.retrorental.backend.model.Vehiculo;
import com.retrorental.backend.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Estadísticas de consumo de combustible por período. Diario, semanal y mensual
 * comparten TODA la lógica: solo cambian los límites del rango. El gasto se
 * calcula por ticket como litros × precioUnitario (el precio vigente que quedó
 * asociado al ticket), y se agrega en memoria — el volumen por período es chico
 * y evita mezclar Double (litros) con BigDecimal (precio) en un SUM de JPQL.
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final TicketRepository ticketRepository;

    /** Estadísticas de un día (default: hoy). */
    @Transactional(readOnly = true)
    public StatsResponse daily(LocalDate fecha, Integer vehiculoId, List<Integer> empleadoIds) {
        LocalDate dia = fecha != null ? fecha : LocalDate.now();
        return statsForRange(dia, dia.plusDays(1), vehiculoId, empleadoIds);
    }

    /** Estadísticas de la semana ISO (lunes a domingo) que contiene la fecha. */
    @Transactional(readOnly = true)
    public StatsResponse weekly(LocalDate fecha, Integer vehiculoId, List<Integer> empleadoIds) {
        LocalDate base = fecha != null ? fecha : LocalDate.now();
        LocalDate lunes = base.with(DayOfWeek.MONDAY);
        return statsForRange(lunes, lunes.plusWeeks(1), vehiculoId, empleadoIds);
    }

    /** Estadísticas del mes calendario que contiene la fecha. */
    @Transactional(readOnly = true)
    public StatsResponse monthly(LocalDate fecha, Integer vehiculoId, List<Integer> empleadoIds) {
        LocalDate base = fecha != null ? fecha : LocalDate.now();
        LocalDate primero = base.withDayOfMonth(1);
        return statsForRange(primero, primero.plusMonths(1), vehiculoId, empleadoIds);
    }

    // Núcleo compartido. desdeInclusive/hastaExclusive son fechas; la consulta
    // filtra por fechaCarga >= desde 00:00 y < hasta 00:00. vehiculoId y
    // empleadoIds son filtros opcionales adicionales aplicados en memoria
    // antes de agregar (null/vacío = sin filtrar).
    private StatsResponse statsForRange(LocalDate desdeInclusive, LocalDate hastaExclusive,
                                         Integer vehiculoId, List<Integer> empleadoIds) {
        LocalDateTime desde = desdeInclusive.atStartOfDay();
        LocalDateTime hasta = hastaExclusive.atStartOfDay();
        List<Ticket> tickets = ticketRepository.findForStats(desde, hasta);

        if (vehiculoId != null) {
            // Filtrar por vehiculo excluye de por si los tickets de
            // herramienta (getVehiculo() es null para esos).
            tickets = tickets.stream()
                .filter(t -> t.getVehiculo() != null && t.getVehiculo().getId().equals(vehiculoId))
                .toList();
        }
        if (empleadoIds != null && !empleadoIds.isEmpty()) {
            tickets = tickets.stream()
                .filter(t -> empleadoIds.contains(t.getPersona().getId()))
                .toList();
        }

        double totalLitros = 0d;
        // Litros cargados a VEHICULOS unicamente. Va aparte de totalLitros
        // porque es el numerador de promedioLitrosPorVehiculo, cuyo denominador
        // (vehiculosActivos) tampoco cuenta herramientas: mezclarlos inflaria el
        // promedio de cada vehiculo con la nafta de las motosierras y bidones.
        double litrosDeVehiculos = 0d;
        BigDecimal gastoTotal = BigDecimal.ZERO;
        // LinkedHashMap: acumula preservando orden de aparición (el orden
        // final lo define el sort por gasto, esto es solo estable).
        Map<Integer, Acumulador> porProveedor = new LinkedHashMap<>();
        Map<Integer, Acumulador> porEmpleado = new LinkedHashMap<>();

        for (Ticket t : tickets) {
            double litros = t.getLitros();
            BigDecimal gasto = t.getPrecio().getPrecioUnitario()
                .multiply(BigDecimal.valueOf(litros));

            totalLitros += litros;
            if (t.getVehiculo() != null) {
                litrosDeVehiculos += litros;
            }
            gastoTotal = gastoTotal.add(gasto);

            Proveedor p = t.getProveedor();
            porProveedor
                .computeIfAbsent(p.getId(), id -> new Acumulador(p.getNombre()))
                .add(litros, gasto);

            Persona persona = t.getPersona();
            porEmpleado
                .computeIfAbsent(persona.getId(), id -> new Acumulador(persona.getNombre() + " " + persona.getApellido()))
                .add(litros, gasto);
        }

        List<ProveedorConsumo> desglosePorProveedor = new ArrayList<>();
        porProveedor.forEach((id, acc) -> desglosePorProveedor.add(new ProveedorConsumo(
            id, acc.etiqueta, redondearLitros(acc.litros), redondear(acc.gasto))));
        // Top proveedores: mayor gasto primero.
        desglosePorProveedor.sort(Comparator.comparing(ProveedorConsumo::gasto, Comparator.reverseOrder()));

        List<EmpleadoConsumo> desglosePorEmpleado = new ArrayList<>();
        porEmpleado.forEach((id, acc) -> desglosePorEmpleado.add(new EmpleadoConsumo(
            id, acc.etiqueta, redondearLitros(acc.litros), redondear(acc.gasto))));
        // Top consumidores: mayor gasto primero.
        desglosePorEmpleado.sort(Comparator.comparing(EmpleadoConsumo::gasto, Comparator.reverseOrder()));

        // vehiculosActivos ya no se calcula por acumulacion: se cuentan los
        // vehiculos distintos que aparecen en los tickets del período. Los
        // tickets de herramienta (getVehiculo() null) quedan afuera: una
        // herramienta no es un vehiculo y no debe inflar este conteo.
        int vehiculosActivos = (int) tickets.stream()
            .map(Ticket::getVehiculo)
            .filter(Objects::nonNull)
            .map(Vehiculo::getId)
            .distinct()
            .count();
        double promedio = vehiculosActivos == 0 ? 0d : litrosDeVehiculos / vehiculosActivos;

        return new StatsResponse(
            desdeInclusive,
            hastaExclusive.minusDays(1), // se expone el último día INCLUIDO
            redondearLitros(totalLitros),
            redondear(gastoTotal),
            tickets.size(),
            vehiculosActivos,
            redondearLitros(promedio),
            desglosePorProveedor,
            desglosePorEmpleado
        );
    }

    private static BigDecimal redondear(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static double redondearLitros(double litros) {
        return BigDecimal.valueOf(litros).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    // Acumulador mutable por proveedor/empleado (solo dentro del cálculo).
    private static final class Acumulador {
        private final String etiqueta;
        private double litros = 0d;
        private BigDecimal gasto = BigDecimal.ZERO;

        private Acumulador(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        private void add(double litros, BigDecimal gasto) {
            this.litros += litros;
            this.gasto = this.gasto.add(gasto);
        }
    }
}
