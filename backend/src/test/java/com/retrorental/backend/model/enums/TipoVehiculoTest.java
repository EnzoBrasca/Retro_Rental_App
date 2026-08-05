package com.retrorental.backend.model.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    // -----------------------------------------------------------------------
    // Identificador: una maquina vial no esta patentada. Se identifica por un
    // numero interno que asigna la empresa, mas corto que una patente.
    // -----------------------------------------------------------------------

    @Test
    void maquina_aceptaInternoCorto() {
        // "M-01" son 4 caracteres: con el formato de patente (minimo 5) no
        // entraria, y la numeracion interna quedaria inutilizable.
        assertTrue("M-01".matches(TipoVehiculo.MAQUINA.formatoIdentificador()));
    }

    @ParameterizedTest
    @EnumSource(value = TipoVehiculo.class, names = {"CAMION", "CAMIONETA"})
    void vehiculosDeRuta_rechazanInternoCorto(TipoVehiculo tipo) {
        // La contracara del test anterior: aflojar el formato para la maquina
        // no puede aflojarlo para el resto de la flota.
        assertFalse("M-01".matches(tipo.formatoIdentificador()));
    }

    @ParameterizedTest
    @EnumSource(TipoVehiculo.class)
    void ningunTipoAceptaSimbolos(TipoVehiculo tipo) {
        assertFalse("!!".matches(tipo.formatoIdentificador()));
    }

    @ParameterizedTest
    @EnumSource(TipoVehiculo.class)
    void patenteVigente_valeEnTodoTipo(TipoVehiculo tipo) {
        // Los vehiculos cargados antes de V6 tienen una patente en el campo
        // identificador, sin importar su tipo. Si el formato de la maquina la
        // rechazara, una maquina ya cargada no se podria editar mas.
        assertTrue("AB123CD".matches(tipo.formatoIdentificador()));
    }

    @Test
    void soloLaMaquinaExigeModelo() {
        // Un interno inventado no dice QUE maquina es; una patente ya la
        // identifica sola, asi que el modelo ahi es opcional.
        assertTrue(TipoVehiculo.MAQUINA.requiereModelo());
        assertFalse(TipoVehiculo.CAMION.requiereModelo());
        assertFalse(TipoVehiculo.CAMIONETA.requiereModelo());
    }
}
