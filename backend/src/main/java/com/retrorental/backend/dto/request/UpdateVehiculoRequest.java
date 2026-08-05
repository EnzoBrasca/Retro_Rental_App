package com.retrorental.backend.dto.request;

import com.retrorental.backend.model.enums.Estado;
import com.retrorental.backend.model.enums.TipoCombustible;
import com.retrorental.backend.model.enums.TipoVehiculo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import com.retrorental.backend.validation.IdentificadorCoherente;
import com.retrorental.backend.validation.VehiculoIdentificable;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Edición de un vehiculo (PUT: reemplazo completo de los campos gestionables).
 * No incluye la baja lógica: eso se hace con DELETE /admin/vehiculos/{id}.
 */
@Data
@IdentificadorCoherente
public class UpdateVehiculoRequest implements VehiculoIdentificable {

    // Patente en un CAMION/CAMIONETA, numero interno en una MAQUINA. El formato
    // no se declara aca porque depende de tipoVehiculo: lo resuelve
    // @IdentificadorCoherente.
    @NotBlank(message = "El identificador es obligatorio")
    private String identificador;

    // Obligatorio solo para MAQUINA (ver @IdentificadorCoherente). Los
    // vehiculos dados de alta antes de V6 lo tienen vacio: la primera edicion
    // de una maquina vieja va a exigir completarlo.
    @Size(max = 60, message = "El modelo no puede superar los 60 caracteres")
    private String modelo;

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

    // Uso acumulado: horas para una MAQUINA, kilometros para el resto. La
    // unidad la define tipoVehiculo, asi que el mensaje se mantiene neutro.
    @NotNull(message = "El uso acumulado es obligatorio")
    @PositiveOrZero(message = "El uso acumulado no puede ser negativo")
    private Integer usoAcumulado;

    @NotNull(message = "El consumo promedio es obligatorio")
    @Positive(message = "El consumo promedio debe ser mayor a cero")
    private BigDecimal consumoPromedio;
}
