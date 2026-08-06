package com.retrorental.backend.validation;

import com.retrorental.backend.model.enums.TipoCombustible;

/**
 * Lo minimo que necesita ver {@link OrigenCargaCoherenteValidator} para
 * validar el origen de una carga (ticket).
 *
 * Mismo motivo que VehiculoIdentificable: la regla se escribe UNA vez sobre
 * esta interfaz en vez de acoplarse a CreateTicketRequest.
 *
 * idPrecio/tipoCombustible tambien dependen del origen: un vehiculo manda
 * idPrecio (el catalogo ya resuelto) y NUNCA tipoCombustible (el combustible
 * es fijo del vehiculo); una herramienta es al reves, manda tipoCombustible
 * (lo elige en el momento) y NUNCA idPrecio (no hay uno resuelto de antemano,
 * lo resuelve el service - ver TicketService.resolvePrecioPorCombustible).
 */
public interface CargaOrigen {

    Integer getIdVehiculo();

    Integer getIdHerramienta();

    Integer getUsoAcumulado();

    Integer getIdPrecio();

    TipoCombustible getTipoCombustible();
}
