package com.retrorental.backend.model.enums;

/**
 * Tipos de combustible, alineados con los productos reales de las estaciones de
 * servicio de Argentina. Los usa tanto el vehiculo (qué carga) como el precio
 * (qué producto se cotiza).
 */
public enum TipoCombustible {
    NAFTA_SUPER,
    NAFTA_PREMIUM,
    GASOIL_GRADO_2,
    GASOIL_GRADO_3,
    GNC,
    // Nafta con aceite para motores 2 tiempos (motosierra). Solo aplica a
    // cargas de HERRAMIENTA, nunca a un vehiculo: por eso solo se agrega al
    // CHECK de precios.tipo_combustible y no al de vehiculos.tipo_combustible
    // (ver V8__agregar_herramientas.sql). Si el proveedor no tiene un vigente
    // propio de mezcla, se crea tomando como referencia inicial el vigente de
    // NAFTA_SUPER de ESE proveedor (ver TicketService.resolvePrecioPorCombustible)
    // y el usuario lo corrige al precio real de la mezcla. Por eso el margen de
    // correccion (+-30) NO aplica a este tipo: el precio de la mezcla se aleja
    // legitimamente del de nafta pura.
    MEZCLA
}
