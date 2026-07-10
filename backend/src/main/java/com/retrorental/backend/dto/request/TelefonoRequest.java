package com.retrorental.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class TelefonoRequest {

    @NotBlank(message = "El código de área es obligatorio")
    @Pattern(regexp = "\\d{2,5}", message = "El código de área debe tener entre 2 y 5 dígitos")
    private String codigoArea;

    @NotBlank(message = "El número de teléfono es obligatorio")
    @Pattern(regexp = "\\d{6,10}", message = "El número de teléfono debe tener entre 6 y 10 dígitos")
    private String numero;
}
