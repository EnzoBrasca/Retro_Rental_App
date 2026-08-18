package com.retrorental.backend.dto.response;

import java.math.BigDecimal;

/**
 * Consumo agregado de un empleado dentro de un período. La lista de estos que
 * devuelve StatsResponse viene ordenada por gasto descendente, de modo que la
 * cabeza de la lista es el ranking de "top consumidores".
 */
public record EmpleadoConsumo(
    Integer empleadoId,
    String nombreCompleto,
    BigDecimal litros,
    BigDecimal gasto
) {
}
