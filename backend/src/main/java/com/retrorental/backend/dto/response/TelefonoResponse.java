package com.retrorental.backend.dto.response;

/**
 * Vista de un teléfono para lectura (ABM de empleados del admin), desglosado
 * en código de área y número para que el form de edición pueda prellenarse
 * directamente con esta respuesta.
 */
public record TelefonoResponse(
    String codigoArea,
    String numero
) {
}
