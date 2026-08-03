package com.retrorental.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 100, message = "El apellido no puede superar los 100 caracteres")
    private String apellido;

    @NotBlank(message = "El documento es obligatorio")
    @Pattern(regexp = "\\d{7,9}", message = "El documento debe tener entre 7 y 9 dígitos")
    private String documento;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres")
    private String password;

    // El rol NO se acepta desde el body: el registro público siempre crea un
    // EMPLEADO. Si el cliente lo mandara, sería el propio usuario decidiendo sus
    // permisos. Los administradores se crean fuera de este endpoint
    // (ver docs/DEPLOYMENT.md).

    @NotNull(message = "El teléfono es obligatorio")
    @Valid
    private TelefonoRequest telefono;
}