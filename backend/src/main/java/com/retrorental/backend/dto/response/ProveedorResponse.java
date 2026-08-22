package com.retrorental.backend.dto.response;

import com.retrorental.backend.model.enums.Servicio;

/**
 * Vista de un proveedor (estación de servicio) para el selector del formulario
 * de carga de tickets.
 */
public record ProveedorResponse(
    Integer id,
    String nombre,
    // Null en el proveedor generico, que no identifica a ninguna empresa.
    String cuit,
    Servicio servicio,
    // El formulario lo necesita para NO prellenar el precio con el vigente de
    // este proveedor: en el generico ese vigente es de otra estacion (ver
    // PrecioCatalogoService.altaSiempre).
    boolean generico
) {
}
