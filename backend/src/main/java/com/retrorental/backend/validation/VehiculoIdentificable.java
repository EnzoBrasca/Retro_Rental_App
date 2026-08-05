package com.retrorental.backend.validation;

import com.retrorental.backend.model.enums.TipoVehiculo;

/**
 * Lo minimo que necesita ver {@link IdentificadorCoherenteValidator} para
 * validar un request de vehiculo.
 *
 * Existe para que la regla se escriba UNA vez y valga tanto para el alta como
 * para la edicion: los dos DTOs la implementan y el validator no sabe cual de
 * los dos esta mirando. Sin esto, el validator tendria que hacer instanceof
 * contra cada DTO y habria que tocarlo cada vez que aparezca un tercero.
 */
public interface VehiculoIdentificable {

    String getIdentificador();

    String getModelo();

    TipoVehiculo getTipoVehiculo();
}
