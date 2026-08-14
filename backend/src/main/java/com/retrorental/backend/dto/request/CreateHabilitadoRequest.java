package com.retrorental.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Alta de un documento en el padron de habilitados (solo admin).
 *
 * El documento usa el mismo formato que RegisterRequest: si aca se admitiera
 * algo que alla no, la habilitacion nunca podria consumirse.
 */
@Data
public class CreateHabilitadoRequest {

    @NotBlank(message = "El documento es obligatorio")
    @Pattern(regexp = "\\d{7,9}", message = "El documento debe tener entre 7 y 9 dígitos")
    private String documento;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 100, message = "El apellido no puede superar los 100 caracteres")
    private String apellido;

    // Opcional: solo para que el administrador reconozca la fila en su lista.
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String nombre;
}
