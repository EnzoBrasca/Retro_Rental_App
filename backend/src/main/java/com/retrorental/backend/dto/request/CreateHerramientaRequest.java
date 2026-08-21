package com.retrorental.backend.dto.request;

import jakarta.validation.constraints.Max;
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

    // Proporcion nafta:aceite (50 = 50:1). OPCIONAL a proposito: el APK 1.7.0
    // sigue instalado y su ABM no la manda; exigirla le romperia el alta de
    // herramientas al admin hasta que actualice. Si no viene, el service pone
    // HerramientaService.RELACION_MEZCLA_DEFAULT.
    @Positive(message = "La relacion de mezcla debe ser mayor a cero")
    @Max(value = 200, message = "La relacion de mezcla no puede superar 200:1")
    private Integer relacionMezcla;
}
