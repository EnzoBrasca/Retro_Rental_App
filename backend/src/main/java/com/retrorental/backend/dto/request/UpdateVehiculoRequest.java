package com.retrorental.backend.dto.request;

import com.retrorental.backend.model.enums.Estado;
import com.retrorental.backend.model.enums.TipoCombustible;
import com.retrorental.backend.model.enums.TipoVehiculo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Edición de un vehiculo (PUT: reemplazo completo de los campos gestionables).
 * No incluye la baja lógica: eso se hace con DELETE /admin/vehiculos/{id}.
 */
@Data
public class UpdateVehiculoRequest {

    @NotBlank(message = "La patente es obligatoria")
    @Pattern(regexp = "^[A-Za-z0-9-]{5,10}$",
        message = "La patente debe tener entre 5 y 10 caracteres alfanuméricos")
    private String patente;

    @NotNull(message = "El tipo de vehiculo es obligatorio")
    private TipoVehiculo tipoVehiculo;

    @NotNull(message = "El tipo de combustible es obligatorio")
    private TipoCombustible tipoCombustible;

    @NotNull(message = "La capacidad del tanque es obligatoria")
    @Positive(message = "La capacidad del tanque debe ser mayor a cero")
    private Integer capacidadTanque;

    @NotNull(message = "El estado es obligatorio")
    private Estado estado;

    @NotNull(message = "La fecha de último mantenimiento es obligatoria")
    @PastOrPresent(message = "La fecha de último mantenimiento no puede ser futura")
    private LocalDate fechaUltimoMantenimiento;

    @NotNull(message = "El kilometraje es obligatorio")
    @PositiveOrZero(message = "El kilometraje no puede ser negativo")
    private Integer kilometraje;

    @NotNull(message = "El consumo promedio es obligatorio")
    @Positive(message = "El consumo promedio debe ser mayor a cero")
    private BigDecimal consumoPromedio;
}
