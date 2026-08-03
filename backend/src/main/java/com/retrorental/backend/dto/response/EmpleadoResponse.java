package com.retrorental.backend.dto.response;

import java.time.LocalDate;

/**
 * Vista de un empleado para lectura (listado/ABM del admin). fechaBaja null
 * indica que el empleado está activo. Teléfono viaja desglosado (no
 * formateado) para que el form de edición del admin pueda prellenarse
 * directamente; tolera que no haya datos cargados (null).
 */
public record EmpleadoResponse(
    Integer id,
    String nombre,
    String apellido,
    String documento,
    String username,
    TelefonoResponse telefono,
    LocalDate fechaAlta,
    LocalDate fechaBaja
) {
}
