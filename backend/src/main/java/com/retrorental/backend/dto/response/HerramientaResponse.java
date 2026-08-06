package com.retrorental.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Vista de una herramienta para lectura (selector del empleado y listado/ABM
 * del admin). fechaBaja null indica que esta activa, misma convencion que
 * VehiculoResponse.
 */
public record HerramientaResponse(
    Integer id,
    String nombre,
    BigDecimal capacidad,
    LocalDate fechaBaja
) {
}
