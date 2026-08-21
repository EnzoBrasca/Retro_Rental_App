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
    // NAFTA_SUPER de ESE proveedor (ver PrecioCatalogoService.resolvePorCombustible)
    // y el usuario lo corrige al precio real de la mezcla. Por eso el margen de
    // correccion (+-30) NO aplica a este tipo: el precio de la mezcla se aleja
    // legitimamente del de nafta pura.
    MEZCLA,

    // Aceite de motor 2 tiempos, el que se mezcla con la nafta. NO es un
    // combustible: es un insumo que se cotiza igual que uno para poder llevarle
    // el precio por proveedor con la misma maquinaria que el resto (vigente
    // unico por (proveedor, producto), historial por fechaHasta, banda de alta).
    //
    // Existe porque el precio de la MEZCLA no se puede observar: nadie vende
    // mezcla en un surtidor. Lo que el empleado SI puede leer es el precio del
    // aceite (esta en la botella) y el de la nafta (ya esta en el catalogo). Con
    // esos dos y la relacion de la herramienta se calcula la mezcla, en vez de
    // pedirle un numero que no existe en ningun lado.
    //
    // Igual que MEZCLA: solo entra al CHECK de precios.tipo_combustible, nunca
    // al de vehiculos (ver V13__agregar_aceite_y_relacion_de_mezcla.sql).
    ACEITE
}
