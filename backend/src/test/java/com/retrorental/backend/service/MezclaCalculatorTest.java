package com.retrorental.backend.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El precio de la mezcla a partir del de la nafta, el del aceite y la
 * proporcion de la maquina.
 *
 * Existe porque el precio de la mezcla NO SE PUEDE OBSERVAR: nadie la vende en
 * un surtidor, no esta en el ticket ni en ninguna etiqueta. Antes se le pedia
 * al empleado un numero que no existia, y terminaba estimado o copiado del de
 * nafta pura -- que es la mezcla SIN el aceite, o sea el error entero.
 */
@Tag("precio")
class MezclaCalculatorTest {

    @Test
    void mezcla50a1_aportaElAceiteEnSuProporcion() {
        // (2000 x 50 + 15000) / 51 = 115000 / 51 = 2254,9019...
        BigDecimal mezcla = MezclaCalculator.calcular(
            new BigDecimal("2000.00"), new BigDecimal("15000.00"), 50);

        assertEquals(new BigDecimal("2254.90"), mezcla);
    }

    @Test
    void unaRelacionMasRica_encareceLaMezcla() {
        // 25:1 lleva el doble de aceite que 50:1, asi que tiene que costar mas.
        BigDecimal a50 = MezclaCalculator.calcular(
            new BigDecimal("2000.00"), new BigDecimal("15000.00"), 50);
        BigDecimal a25 = MezclaCalculator.calcular(
            new BigDecimal("2000.00"), new BigDecimal("15000.00"), 25);

        assertEquals(new BigDecimal("2500.00"), a25);
        assertTrue(a25.compareTo(a50) > 0);
    }

    /**
     * EL test de precision. La formula se evalua como (nafta x r + aceite)/(r+1)
     * y no como nafta x r/(r+1) + aceite x 1/(r+1): son identicas en algebra,
     * pero la segunda hace DOS divisiones y redondea las dos fracciones antes de
     * multiplicar.
     *
     * Con 50:1 esa version daria 2000 x 0,98 + 15000 x 0,02 = 2260,00, casi seis
     * pesos por litro de diferencia sobre un valor de 2254,90. Una division sola
     * al final no tiene ese error.
     */
    @Test
    void seEvaluaConUnaSolaDivision() {
        BigDecimal mezcla = MezclaCalculator.calcular(
            new BigDecimal("2000.00"), new BigDecimal("15000.00"), 50);

        assertEquals(new BigDecimal("2254.90"), mezcla);
        assertNotEquals(new BigDecimal("2260.00"), mezcla);
    }

    @Test
    void aceiteAlMismoPrecioQueLaNafta_dejaElPrecioDeLaNafta() {
        // Si el aceite costara lo mismo que la nafta, mezclarlos no cambia nada:
        // sirve de prueba de cordura de la formula.
        BigDecimal mezcla = MezclaCalculator.calcular(
            new BigDecimal("2000.00"), new BigDecimal("2000.00"), 50);

        assertEquals(0, mezcla.compareTo(new BigDecimal("2000.00")));
    }

    @Test
    void relacionUnoAUno_esElPromedioDeLosDos() {
        BigDecimal mezcla = MezclaCalculator.calcular(
            new BigDecimal("2000.00"), new BigDecimal("3000.00"), 1);

        assertEquals(0, mezcla.compareTo(new BigDecimal("2500.00")));
    }

    @Test
    void elResultadoSiempreTieneDosDecimales() {
        // precios.precio_unitario es numeric(10,2): mas escala no entra.
        BigDecimal mezcla = MezclaCalculator.calcular(
            new BigDecimal("1999.99"), new BigDecimal("14999.99"), 37);

        assertEquals(2, mezcla.scale());
    }

    // ------------------------------------------------------------ guardas

    @Test
    void sinPrecioDeNafta_rechaza() {
        assertThrows(IllegalArgumentException.class,
            () -> MezclaCalculator.calcular(null, new BigDecimal("15000.00"), 50));
    }

    @Test
    void sinPrecioDeAceite_rechaza() {
        assertThrows(IllegalArgumentException.class,
            () -> MezclaCalculator.calcular(new BigDecimal("2000.00"), null, 50));
    }

    @Test
    void relacionNoPositiva_rechaza() {
        // r = 0 haria una mezcla de puro aceite y r < 0 no significa nada. Se
        // corta explicito en vez de dejar que la division haga cualquier cosa.
        assertThrows(IllegalArgumentException.class,
            () -> MezclaCalculator.calcular(new BigDecimal("2000.00"), new BigDecimal("15000.00"), 0));
        assertThrows(IllegalArgumentException.class,
            () -> MezclaCalculator.calcular(new BigDecimal("2000.00"), new BigDecimal("15000.00"), -5));
    }
}
