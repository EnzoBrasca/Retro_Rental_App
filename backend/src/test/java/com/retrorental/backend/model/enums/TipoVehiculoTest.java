package com.retrorental.backend.model.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * La unidad del contador de uso se deriva del tipo de vehiculo. Es la regla que
 * evita el error que reporto el cliente: cargar kilometros en una maquina vial,
 * que en realidad mide horas de horometro.
 */
class TipoVehiculoTest {

    @Test
    void maquina_mideEnHoras() {
        assertEquals(UnidadUso.HORAS, TipoVehiculo.MAQUINA.unidadUso());
    }

    @ParameterizedTest
    @EnumSource(value = TipoVehiculo.class, names = {"CAMION", "CAMIONETA"})
    void vehiculosDeRuta_midenEnKilometros(TipoVehiculo tipo) {
        assertEquals(UnidadUso.KM, tipo.unidadUso());
    }

    /**
     * Si manana se agrega un tipo nuevo al enum, este test obliga a decidir su
     * unidad a conciencia en vez de heredar KM por descarte.
     */
    @ParameterizedTest
    @EnumSource(TipoVehiculo.class)
    void todoTipoTieneUnidad(TipoVehiculo tipo) {
        assertEquals(
            tipo == TipoVehiculo.MAQUINA ? UnidadUso.HORAS : UnidadUso.KM,
            tipo.unidadUso());
    }
}
