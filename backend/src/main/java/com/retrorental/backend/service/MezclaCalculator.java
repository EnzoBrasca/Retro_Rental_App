package com.retrorental.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Precio por litro de la mezcla (nafta con aceite, motores 2 tiempos) a partir
 * del precio de la nafta, el del aceite y la proporcion de la maquina.
 *
 * POR QUE EXISTE. El precio de la mezcla NO SE PUEDE OBSERVAR: nadie la vende
 * en un surtidor, no esta en el ticket ni en ninguna etiqueta. Antes se le
 * pedia al empleado ese numero y se le ofrecia como valor inicial el de
 * NAFTA_SUPER -- que es la mezcla SIN el aceite, o sea el error entero. Lo que
 * el empleado SI puede leer es el precio del aceite (esta en la botella); el de
 * la nafta ya vive en el catalogo. Con esos dos y la relacion, la mezcla sale
 * de una cuenta en vez de una estimacion.
 *
 * La relacion se expresa como su primer termino: 50 = 50:1, o sea 50 partes de
 * nafta por 1 de aceite. Un litro de mezcla lleva entonces r/(r+1) litros de
 * nafta y 1/(r+1) de aceite.
 */
public final class MezclaCalculator {

    private MezclaCalculator() {
    }

    /**
     * Precio por litro de la mezcla, con la escala de precios.precio_unitario
     * (numeric(10,2)).
     *
     * Se evalua como (nafta x r + aceite) / (r + 1) y NO como
     * nafta x r/(r+1) + aceite x 1/(r+1). Son la misma identidad algebraica,
     * pero la segunda divide dos veces y redondea las dos fracciones antes de
     * multiplicarlas por precios de miles de pesos, asi que el error de
     * redondeo entra amplificado: con 50:1, nafta a 2000 y aceite a 15000 daria
     * 2260,00 contra los 2254,90 correctos. Una sola division al final, sobre
     * numeros ya sumados, no arrastra ese error (mismo criterio que
     * ConsumoCalculator, que acumula y divide una vez).
     *
     * @param precioNafta  precio por litro de la nafta base, no nulo
     * @param precioAceite precio por litro del aceite 2 tiempos, no nulo
     * @param relacion     partes de nafta por parte de aceite (50 = 50:1), > 0
     */
    public static BigDecimal calcular(BigDecimal precioNafta, BigDecimal precioAceite,
                                      int relacion) {
        if (precioNafta == null) {
            throw new IllegalArgumentException("Falta el precio de la nafta para calcular la mezcla");
        }
        if (precioAceite == null) {
            throw new IllegalArgumentException("Falta el precio del aceite para calcular la mezcla");
        }
        // r = 0 seria una mezcla de puro aceite y r < 0 no significa nada. Se
        // corta explicito en vez de dejar que la division haga cualquier cosa.
        if (relacion <= 0) {
            throw new IllegalArgumentException(
                "La relacion de mezcla debe ser mayor a cero, y llego " + relacion);
        }

        BigDecimal partes = BigDecimal.valueOf(relacion);
        return precioNafta.multiply(partes)
            .add(precioAceite)
            .divide(partes.add(BigDecimal.ONE), 2, RoundingMode.HALF_UP);
    }
}
