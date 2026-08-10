package com.retrorental.backend.dto.response;

import com.retrorental.backend.model.enums.UnidadUso;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Estadísticas de consumo agregadas para un período. La misma forma sirve para
 * el día, la semana o el mes: solo cambia el rango [desde, hasta].
 *
 * desde/hasta son ambos INCLUSIVOS (para el día, desde == hasta).
 */
public record StatsResponse(
    LocalDate desde,
    LocalDate hasta,
    double totalLitros,
    BigDecimal gastoTotal,
    long cantidadRegistros,
    int vehiculosActivos,
    double promedioLitrosPorVehiculo,

    /**
     * Consumo real del vehículo filtrado durante el período, en la unidad que
     * indica {@link #unidadUso()} (L/h en una máquina, L/100km en el resto).
     *
     * NULL en dos casos, y el cliente debe distinguirlos de un cero:
     * - No hay un vehículo seleccionado. Promediar el consumo de toda la flota
     *   mezclaría L/h con L/100km, que no son la misma magnitud.
     * - No hay dos lecturas del contador con las que formar un intervalo.
     */
    BigDecimal consumoPeriodo,
    UnidadUso unidadUso,

    List<ProveedorConsumo> desglosePorProveedor,
    List<EmpleadoConsumo> desglosePorEmpleado
) {
}
