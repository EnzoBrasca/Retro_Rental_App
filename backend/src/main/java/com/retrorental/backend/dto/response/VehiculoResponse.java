package com.retrorental.backend.dto.response;

import com.retrorental.backend.model.enums.Estado;
import com.retrorental.backend.model.enums.TipoCombustible;
import com.retrorental.backend.model.enums.TipoVehiculo;
import com.retrorental.backend.model.enums.UnidadUso;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Vista de un vehiculo para lectura (selector del empleado y listado/ABM del
 * admin). fechaBaja null indica que el vehiculo está activo.
 *
 * Incluye capacidad, uso acumulado y consumo promedio para que las tarjetas
 * del front muestren datos reales.
 *
 * unidadUso viaja junto a usoAcumulado para que el cliente sepa que esta
 * mostrando (HORAS en una maquina vial, KM en un camion) sin tener que
 * reimplementar la regla. Se deriva de tipoVehiculo, no se persiste.
 */
public record VehiculoResponse(
    Integer id,
    String patente,
    TipoVehiculo tipoVehiculo,
    TipoCombustible tipoCombustible,
    Estado estado,
    Integer capacidadTanque,
    Integer usoAcumulado,
    UnidadUso unidadUso,
    BigDecimal consumoPromedio,
    // Consumo de las ultimas cargas. Null hasta que haya dos con lectura.
    BigDecimal consumoReciente,
    LocalDate fechaBaja,
    // Operario que usó el vehiculo por última vez (se actualiza en cada carga de
    // ticket). Null = sin uso registrado todavía. El nombre/apellido viajan para
    // que la card muestre "en uso por X" sin una segunda llamada.
    Integer idOperario,
    String operarioNombre,
    String operarioApellido
) {
}
