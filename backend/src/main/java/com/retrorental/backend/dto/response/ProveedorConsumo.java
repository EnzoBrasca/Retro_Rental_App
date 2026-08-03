package com.retrorental.backend.dto.response;

import java.math.BigDecimal;

/**
 * Consumo agregado de un proveedor dentro de un período. La lista de estos que
 * devuelve StatsResponse viene ordenada por gasto descendente, de modo que la
 * cabeza de la lista es el ranking de "top proveedores".
 */
public record ProveedorConsumo(
    Integer proveedorId,
    String nombre,
    double litros,
    BigDecimal gasto
) {
}
