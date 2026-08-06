package com.retrorental.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Alta de una herramienta (solo admin). A diferencia de un vehiculo, no lleva
 * identificador, modelo, tipo, uso acumulado ni combustible fijo: eso se
 * elige carga por carga (ver CreateTicketRequest.idHerramienta).
 */
@Data
public class CreateHerramientaRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotNull(message = "La capacidad es obligatoria")
    @Positive(message = "La capacidad debe ser mayor a cero")
    private BigDecimal capacidad;
}
