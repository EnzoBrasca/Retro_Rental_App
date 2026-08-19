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
 *
 * Lo mismo vale para el identificador: viaja el valor, y el cliente decide si
 * lo rotula "Patente" o "Interno" segun tipoVehiculo. La etiqueta no se manda
 * desde el backend porque es una decision de presentacion.
 */
public record VehiculoResponse(
    Integer id,
    // Patente en CAMION/CAMIONETA, numero interno en MAQUINA.
    String identificador,
    // Modelo descriptivo (ej. "CAT 320D"). Null en los vehiculos anteriores a
    // V6 y en los camiones/camionetas que no lo cargaron.
    String modelo,
    TipoVehiculo tipoVehiculo,
    TipoCombustible tipoCombustible,
    Estado estado,
    Integer capacidadTanque,
    Integer usoAcumulado,
    UnidadUso unidadUso,
    BigDecimal consumoPromedio,
    // Consumo de las ultimas cargas. Null hasta que haya dos con lectura.
    BigDecimal consumoReciente,
    // Viaja porque UpdateVehiculoRequest la exige en cada edicion: sin ella el
    // ABM del admin no tiene de donde leer el valor guardado y lo completa con
    // la fecha de hoy, pisando el mantenimiento real del vehiculo.
    LocalDate fechaUltimoMantenimiento,
    LocalDate fechaBaja,
    // Operario que usó el vehiculo por última vez (se actualiza en cada carga de
    // ticket). Null = sin uso registrado todavía. El nombre/apellido viajan para
    // que la card muestre "en uso por X" sin una segunda llamada.
    Integer idOperario,
    String operarioNombre,
    String operarioApellido
) {
}
