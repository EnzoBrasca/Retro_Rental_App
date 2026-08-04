package com.retrorental.backend.model.enums;

/**
 * Unidad en la que se mide el uso acumulado de un vehiculo.
 *
 * NO se persiste: se deriva de {@link TipoVehiculo#unidadUso()}. Guardarla
 * seria duplicar un dato derivable y abrir la puerta a que contradiga al tipo
 * (una MAQUINA con unidad KM). Viaja en VehiculoResponse para que el cliente
 * renderice la etiqueta correcta sin reimplementar la regla.
 */
public enum UnidadUso {
    KM,
    HORAS
}
