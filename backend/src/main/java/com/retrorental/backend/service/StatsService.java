package com.retrorental.backend.service;

import com.retrorental.backend.dto.response.EmpleadoConsumo;
import com.retrorental.backend.dto.response.ProveedorConsumo;
import com.retrorental.backend.dto.response.StatsResponse;
import com.retrorental.backend.model.Persona;
import com.retrorental.backend.model.Proveedor;
import com.retrorental.backend.model.Ticket;
import com.retrorental.backend.model.Vehiculo;
import com.retrorental.backend.model.enums.UnidadUso;
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
 * asociado al ticket), y se agrega en memoria — el volumen por período es chico.
 *
 * Todo el dinero y todos los litros se acumulan en BigDecimal. Antes los litros
 * eran Double y el argumento para dejarlos así era evitar mezclar tipos en un
 * SUM de JPQL; ese argumento cayó cuando se vio que el operando ya entraba
 * contaminado por el error de representación binaria y que multiplicarlo por un
 * BigDecimal no lo recuperaba (ver docs/BACKEND-AUDIT.md, DB-04).
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

        // Los dos filtros opcionales viajan a la CONSULTA. Antes se traia el
        // rango entero —con precio, vehiculo, herramienta, persona y proveedor
        // por cada fila— y se descartaba en memoria lo que no correspondia. Para
        // un mes de una flota grande eso hidrata muchisimas mas filas de las que
        // el resultado necesita (ver docs/BACKEND-AUDIT.md, SVC-02).
        //
        // La consulta exige una lista no nula, asi que se normaliza aca. El
        // valor de relleno no se usa nunca: cuando filtrarPorEmpleado es false,
        // el predicado del IN ni se evalua.
        boolean filtrarPorEmpleado = empleadoIds != null && !empleadoIds.isEmpty();
        List<Integer> idsParaLaConsulta = filtrarPorEmpleado ? empleadoIds : List.of(-1);

        // Filtrar por vehiculo excluye de por si los tickets de herramienta
        // (id_vehiculo es null para esos), igual que antes.
        List<Ticket> tickets = ticketRepository.findForStats(
            desde, hasta, vehiculoId, filtrarPorEmpleado, idsParaLaConsulta);

        BigDecimal totalLitros = BigDecimal.ZERO;
        // Litros cargados a VEHICULOS unicamente. Va aparte de totalLitros
        // porque es el numerador de promedioLitrosPorVehiculo, cuyo denominador
        // (vehiculosActivos) tampoco cuenta herramientas: mezclarlos inflaria el
        // promedio de cada vehiculo con la nafta de las motosierras y bidones.
        BigDecimal litrosDeVehiculos = BigDecimal.ZERO;
        BigDecimal gastoTotal = BigDecimal.ZERO;
        // LinkedHashMap: acumula preservando orden de aparición (el orden
        // final lo define el sort por gasto, esto es solo estable).
        Map<Integer, Acumulador> porProveedor = new LinkedHashMap<>();
        Map<Integer, Acumulador> porEmpleado = new LinkedHashMap<>();

        for (Ticket t : tickets) {
            BigDecimal litros = t.getLitros();
            // Multiplicacion EXACTA: los dos operandos son BigDecimal. Antes
            // litros entraba como double y ya venia contaminado, asi que el
            // BigDecimal del precio no alcanzaba (ver docs/BACKEND-AUDIT.md,
            // DB-04).
            BigDecimal gasto = t.getPrecio().getPrecioUnitario().multiply(litros);

            totalLitros = totalLitros.add(litros);
            if (t.getVehiculo() != null) {
                litrosDeVehiculos = litrosDeVehiculos.add(litros);
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
            id, acc.etiqueta, redondear(acc.litros), redondear(acc.gasto))));
        // Top proveedores: mayor gasto primero.
        desglosePorProveedor.sort(Comparator.comparing(ProveedorConsumo::gasto, Comparator.reverseOrder()));

        List<EmpleadoConsumo> desglosePorEmpleado = new ArrayList<>();
        porEmpleado.forEach((id, acc) -> desglosePorEmpleado.add(new EmpleadoConsumo(
            id, acc.etiqueta, redondear(acc.litros), redondear(acc.gasto))));
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
        // La division SIEMPRE lleva escala y modo de redondeo explicitos: sin
        // ellos, BigDecimal.divide tira ArithmeticException cuando el resultado
        // es periodico (ej. 10 L / 3 vehiculos).
        BigDecimal promedio = vehiculosActivos == 0
            ? BigDecimal.ZERO
            : litrosDeVehiculos.divide(BigDecimal.valueOf(vehiculosActivos), 2, RoundingMode.HALF_UP);

        Consumo consumo = consumoDelPeriodo(vehiculoId, desde, hasta);

        return new StatsResponse(
            desdeInclusive,
            hastaExclusive.minusDays(1), // se expone el último día INCLUIDO
            redondear(totalLitros),
            redondear(gastoTotal),
            tickets.size(),
            vehiculosActivos,
            redondear(promedio),
            consumo.valor(),
            consumo.unidad(),
            desglosePorProveedor,
            desglosePorEmpleado
        );
    }

    /** Consumo del vehículo en el período, o los dos campos en null si no hay dato. */
    private record Consumo(BigDecimal valor, UnidadUso unidad) {
        private static final Consumo SIN_DATO = new Consumo(null, null);
    }

    /**
     * Consumo real del vehículo seleccionado durante el período.
     *
     * <h2>Por qué no sale de los tickets que ya tenemos</h2>
     * El resto del panel agrega los tickets del rango y listo. El consumo NO
     * puede: por el método de tanque lleno, los litros de una carga reponen lo
     * gastado desde la carga ANTERIOR. La primera carga del rango solo fija la
     * línea base, así que con los tickets del rango solos se pierde un intervalo
     * — y en un rango diario, que suele tener una sola carga, se pierden todos.
     * Por eso se trae el histórico del vehículo y se toma además la última carga
     * previa a {@code desde} como línea base.
     *
     * <h2>Por qué ignora el filtro de empleado</h2>
     * El intervalo entre dos cargas necesita las cargas CONSECUTIVAS. Filtrando
     * por empleado se caen del medio las que hizo otro: el intervalo conserva
     * todo el uso pero pierde los litros que lo repusieron, y el consumo sale
     * sistemáticamente bajo. No es una aproximación, es un número equivocado. El
     * consumo es una propiedad del vehículo, no de quién cargó.
     */
    private Consumo consumoDelPeriodo(Integer vehiculoId, LocalDateTime desde, LocalDateTime hasta) {
        if (vehiculoId == null) {
            // Sin vehículo seleccionado no hay consumo que informar: promediar
            // la flota mezclaría L/h de las máquinas con L/100km del resto.
            return Consumo.SIN_DATO;
        }

        // Misma consulta que alimenta el consumo persistido del vehículo (ver
        // TicketService.recalcularConsumo): cargas vigentes con lectura, en
        // orden de lectura. Las lecturas son monótonas (una carga que retrocede
        // el contador se rechaza al crearse), así que ese orden es también el
        // cronológico.
        // Proyeccion de cuatro columnas, no entidades: el calculo solo necesita
        // lectura, litros, fecha y el tipo del vehiculo. El tipo viene en la
        // misma consulta, lo que ademas elimina el SELECT lazy que antes
        // disparaba `cargas.get(0).getVehiculo()` (ver docs/BACKEND-AUDIT.md,
        // SVC-06).
        List<TicketRepository.CargaParaStats> cargasDelVehiculo =
            ticketRepository.findCargasParaStats(vehiculoId);
        if (cargasDelVehiculo.isEmpty()) {
            return Consumo.SIN_DATO;
        }

        TicketRepository.CargaParaStats lineaBase = null;
        List<TicketRepository.CargaParaStats> delPeriodo = new ArrayList<>();
        for (TicketRepository.CargaParaStats t : cargasDelVehiculo) {
            if (t.getFechaCarga().isBefore(desde)) {
                lineaBase = t; // se queda la última previa al rango
            } else if (t.getFechaCarga().isBefore(hasta)) {
                delPeriodo.add(t);
            }
            // Las posteriores al rango quedan afuera: el dato tiene que
            // corresponder al período que el admin tiene en pantalla.
        }

        List<ConsumoCalculator.Carga> cargas = new ArrayList<>();
        if (lineaBase != null) {
            cargas.add(new ConsumoCalculator.Carga(lineaBase.getUsoAcumulado(), lineaBase.getLitros()));
        }
        for (TicketRepository.CargaParaStats t : delPeriodo) {
            cargas.add(new ConsumoCalculator.Carga(t.getUsoAcumulado(), t.getLitros()));
        }

        UnidadUso unidad = cargasDelVehiculo.get(0).getTipoVehiculo().unidadUso();
        // La ventana del consumo "reciente" no interesa acá: el período ya ES la
        // ventana. Pasando el total de cargas, reciente == histórico y se ignora.
        BigDecimal valor = ConsumoCalculator.calcular(cargas, unidad, cargas.size()).historico();

        // Sin valor no se informa unidad tampoco: una unidad suelta invitaría al
        // cliente a pintar un "0 L/h" que nadie calculó.
        return valor == null ? Consumo.SIN_DATO : new Consumo(valor, unidad);
    }

    private static BigDecimal redondear(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    // Acumulador mutable por proveedor/empleado (solo dentro del cálculo).
    private static final class Acumulador {
        private final String etiqueta;
        private BigDecimal litros = BigDecimal.ZERO;
        private BigDecimal gasto = BigDecimal.ZERO;

        private Acumulador(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        private void add(BigDecimal litros, BigDecimal gasto) {
            this.litros = this.litros.add(litros);
            this.gasto = this.gasto.add(gasto);
        }
    }
}
