package com.retrorental.backend.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Fila del padron tal como la ve el administrador.
 *
 * {@code registrado} viene resuelto desde el backend en lugar de dejar que el
 * mobile deduzca el estado mirando si fechaUso es null: el estado de una
 * habilitacion lo define el backend, y asi la pantalla no puede interpretarlo
 * distinto.
 */
public record HabilitadoResponse(
    Integer id,
    String documento,
    String apellido,
    String nombre,
    LocalDate fechaAlta,
    boolean registrado,
    LocalDateTime fechaUso,
    // Username de la cuenta creada con esta habilitacion. null si sigue sin usarse.
    String username
) {}
