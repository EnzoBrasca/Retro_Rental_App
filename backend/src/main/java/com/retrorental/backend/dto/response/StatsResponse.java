package com.retrorental.backend.dto.response;

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
    List<ProveedorConsumo> desglosePorProveedor,
    List<EmpleadoConsumo> desglosePorEmpleado
) {
}
