package com.retrorental.backend.dto.response;

import com.retrorental.backend.model.enums.Servicio;
import com.retrorental.backend.model.enums.TipoCombustible;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Vista de un precio para el selector del formulario de carga de tickets.
 * `tipoCombustible` identifica el producto (ej. GASOIL_GRADO_2); es null cuando
 * el servicio no es COMBUSTIBLE. `idProveedor` indica de qué estación es ese
 * precio (null en precios legacy sin proveedor); la app elige el precio por
 * (proveedor + combustible del vehículo).
 */
public record PrecioResponse(
    Integer id,
    TipoCombustible tipoCombustible,
    BigDecimal precioUnitario,
    Servicio servicio,
    Integer idProveedor,
    LocalDate fechaDesde,
    LocalDate fechaHasta
) {
}
