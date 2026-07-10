package com.retrorental.backend.dto.response;

import java.math.BigDecimal;

/**
 * Consumo agregado de un vehiculo dentro de un período. La lista de estos que
 * devuelve StatsResponse viene ordenada por litros descendente, de modo que la
 * cabeza de la lista es el ranking de "top consumidores".
 */
public record VehiculoConsumo(
    Integer vehiculoId,
    String patente,
    double litros,
    BigDecimal gasto
) {
}
