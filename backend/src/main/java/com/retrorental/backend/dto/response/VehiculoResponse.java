package com.retrorental.backend.dto.response;

import com.retrorental.backend.model.enums.Estado;
import com.retrorental.backend.model.enums.TipoCombustible;
import com.retrorental.backend.model.enums.TipoVehiculo;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Vista de un vehiculo para lectura (selector del empleado y listado/ABM del
 * admin). fechaBaja null indica que el vehiculo está activo.
 *
 * Incluye capacidad, kilometraje y consumo promedio para que las tarjetas del
 * front muestren datos reales (antes eran mock).
 */
public record VehiculoResponse(
    Integer id,
    String patente,
    TipoVehiculo tipoVehiculo,
    TipoCombustible tipoCombustible,
    Estado estado,
    Integer capacidadTanque,
    Integer kilometraje,
    BigDecimal consumoPromedio,
    LocalDate fechaBaja,
    // Operario que usó el vehiculo por última vez (se actualiza en cada carga de
    // ticket). Null = sin uso registrado todavía. El nombre/apellido viajan para
    // que la card muestre "en uso por X" sin una segunda llamada.
    Integer idOperario,
    String operarioNombre,
    String operarioApellido
) {
}
