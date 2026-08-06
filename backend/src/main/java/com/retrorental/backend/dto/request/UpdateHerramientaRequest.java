package com.retrorental.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Edicion de una herramienta (PUT: reemplazo completo de los campos
 * gestionables). No incluye la baja logica: eso se hace con
 * DELETE /admin/herramientas/{id}.
 */
@Data
public class UpdateHerramientaRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotNull(message = "La capacidad es obligatoria")
    @Positive(message = "La capacidad debe ser mayor a cero")
    private BigDecimal capacidad;
}
