package com.retrorental.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Edición de un empleado (PUT: reemplazo completo de los campos gestionables).
 * Sin password ni documento: la baja lógica se hace con
 * DELETE /admin/empleados/{id}.
 */
@Data
public class UpdateEmpleadoRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 100, message = "El apellido no puede superar los 100 caracteres")
    private String apellido;

    @NotNull(message = "El teléfono es obligatorio")
    @Valid
    private TelefonoRequest telefono;
}
