package com.retrorental.backend.dto.response;

import com.retrorental.backend.model.enums.Servicio;

/**
 * Vista de un proveedor (estación de servicio) para el selector del formulario
 * de carga de tickets.
 */
public record ProveedorResponse(
    Integer id,
    String nombre,
    String cuit,
    Servicio servicio
) {
}
