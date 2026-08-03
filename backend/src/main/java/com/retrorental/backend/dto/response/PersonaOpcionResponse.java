package com.retrorental.backend.dto.response;

import com.retrorental.backend.model.enums.Rol;

/**
 * Opción de persona (empleado o administrador) para poblar filtros de
 * selección en el panel admin, por ejemplo el filtro por empleado de las
 * estadísticas de consumo.
 */
public record PersonaOpcionResponse(
    Integer id,
    String nombre,
    String apellido,
    String username,
    Rol rol
) {
}
