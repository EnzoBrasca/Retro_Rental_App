package com.retrorental.backend.service;

import com.retrorental.backend.dto.response.StatsResponse;
import com.retrorental.backend.dto.response.VehiculoConsumo;
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
    public StatsResponse daily(LocalDate fecha) {
        LocalDate dia = fecha != null ? fecha : LocalDate.now();
        return statsForRange(dia, dia.plusDays(1));
    }

    /** Estadísticas de la semana ISO (lunes a domingo) que contiene la fecha. */
    @Transactional(readOnly = true)
    public StatsResponse weekly(LocalDate fecha) {
        LocalDate base = fecha != null ? fecha : LocalDate.now();
        LocalDate lunes = base.with(DayOfWeek.MONDAY);
        return statsForRange(lunes, lunes.plusWeeks(1));
    }

    /** Estadísticas del mes calendario que contiene la fecha. */
    @Transactional(readOnly = true)
    public StatsResponse monthly(LocalDate fecha) {
        LocalDate base = fecha != null ? fecha : LocalDate.now();
        LocalDate primero = base.withDayOfMonth(1);
        return statsForRange(primero, primero.plusMonths(1));
    }

    // Núcleo compartido. desdeInclusive/hastaExclusive son fechas; la consulta
    // filtra por fechaCarga >= desde 00:00 y < hasta 00:00.
    private StatsResponse statsForRange(LocalDate desdeInclusive, LocalDate hastaExclusive) {
        LocalDateTime desde = desdeInclusive.atStartOfDay();
        LocalDateTime hasta = hastaExclusive.atStartOfDay();
        List<Ticket> tickets = ticketRepository.findForStats(desde, hasta);

        double totalLitros = 0d;
        BigDecimal gastoTotal = BigDecimal.ZERO;
        // LinkedHashMap: acumula por vehiculo preservando orden de aparición
        // (el orden final lo define el sort por litros, esto es solo estable).
        Map<Integer, Acumulador> porVehiculo = new LinkedHashMap<>();

        for (Ticket t : tickets) {
            double litros = t.getLitros();
            BigDecimal gasto = t.getPrecio().getPrecioUnitario()
                .multiply(BigDecimal.valueOf(litros));

            totalLitros += litros;
            gastoTotal = gastoTotal.add(gasto);

            Vehiculo v = t.getVehiculo();
            porVehiculo
                .computeIfAbsent(v.getId(), id -> new Acumulador(v.getPatente()))
                .add(litros, gasto);
        }

        List<VehiculoConsumo> desglose = new ArrayList<>();
        porVehiculo.forEach((id, acc) -> desglose.add(new VehiculoConsumo(
            id, acc.patente, redondearLitros(acc.litros), redondear(acc.gasto))));
        // Top consumidores: mayor consumo primero.
        desglose.sort(Comparator.comparingDouble(VehiculoConsumo::litros).reversed());

        int vehiculosActivos = porVehiculo.size();
        double promedio = vehiculosActivos == 0 ? 0d : totalLitros / vehiculosActivos;

        return new StatsResponse(
            desdeInclusive,
            hastaExclusive.minusDays(1), // se expone el último día INCLUIDO
            redondearLitros(totalLitros),
            redondear(gastoTotal),
            tickets.size(),
            vehiculosActivos,
            redondearLitros(promedio),
            desglose
        );
    }

    private static BigDecimal redondear(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static double redondearLitros(double litros) {
        return BigDecimal.valueOf(litros).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    // Acumulador mutable por vehiculo (solo dentro del cálculo).
    private static final class Acumulador {
        private final String patente;
        private double litros = 0d;
        private BigDecimal gasto = BigDecimal.ZERO;

        private Acumulador(String patente) {
            this.patente = patente;
        }

        private void add(double litros, BigDecimal gasto) {
            this.litros += litros;
            this.gasto = this.gasto.add(gasto);
        }
    }
}
