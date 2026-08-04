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
 * Alta de un vehiculo. La patente debe ser única (se valida en el service).
 * "estado" es opcional: un vehiculo nuevo se da de alta DISPONIBLE salvo que se
 * indique otro.
 */
@Data
public class CreateVehiculoRequest {

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

    // Opcional: si no viene, el vehiculo se crea DISPONIBLE.
    private Estado estado;

    @NotNull(message = "La fecha de último mantenimiento es obligatoria")
    @PastOrPresent(message = "La fecha de último mantenimiento no puede ser futura")
    private LocalDate fechaUltimoMantenimiento;

    // Uso acumulado: horas para una MAQUINA, kilometros para el resto. La
    // unidad la define tipoVehiculo, asi que el mensaje se mantiene neutro.
    @NotNull(message = "El uso acumulado es obligatorio")
    @PositiveOrZero(message = "El uso acumulado no puede ser negativo")
    private Integer usoAcumulado;

    @NotNull(message = "El consumo promedio es obligatorio")
    @Positive(message = "El consumo promedio debe ser mayor a cero")
    private BigDecimal consumoPromedio;
}
