package com.retrorental.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.retrorental.backend.model.enums.UnidadUso;
import com.retrorental.backend.service.ConsumoCalculator.Carga;
import com.retrorental.backend.service.ConsumoCalculator.Consumo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

class ConsumoCalculatorTest {

    private static final int VENTANA = 10;

    private static Consumo calcularKm(List<Carga> cargas) {
        return ConsumoCalculator.calcular(cargas, UnidadUso.KM, VENTANA);
    }

    // ------------------------------------------------------ datos insuficientes

    @Test
    void sinCargas_noHayConsumo() {
        Consumo c = calcularKm(List.of());
        assertNull(c.historico());
        assertNull(c.reciente());
    }

    @Test
    void unaSolaCarga_noHayConsumo() {
        // La primera carga solo fija la linea base: no genera intervalo.
        Consumo c = calcularKm(List.of(new Carga(1000, 50)));
        assertNull(c.historico());
        assertNull(c.reciente());
    }

    @Test
    void lecturasIguales_noHayConsumo() {
        // Sin uso entre cargas no hay de que derivar un consumo (y evita
        // dividir por cero).
        Consumo c = calcularKm(List.of(new Carga(1000, 50), new Carga(1000, 40)));
        assertNull(c.historico());
    }

    // ------------------------------------------------------------- calculo base

    @Test
    void dosCargas_usaSoloLosLitrosDeLaSegunda() {
        // 100 km recorridos, 10 L repuestos -> 10 L/100km. Los 50 L de la
        // primera carga NO entran: son la linea base.
        Consumo c = calcularKm(List.of(new Carga(1000, 50), new Carga(1100, 10)));
        assertEquals(new BigDecimal("10.00"), c.historico());
    }

    @Test
    void maquina_seExpresaEnLitrosPorHora() {
        // 20 horas de uso, 40 L -> 2 L/h. Sin multiplicar por 100.
        Consumo c = ConsumoCalculator.calcular(
            List.of(new Carga(100, 60), new Carga(120, 40)), UnidadUso.HORAS, VENTANA);
        assertEquals(new BigDecimal("2.00"), c.historico());
    }

    /**
     * El punto central del calculo: se acumulan totales y se divide al final.
     * Promediar el consumo de cada intervalo le daria el mismo peso a uno de
     * 100 km que a uno de 10 e inflaria el numero.
     */
    @Test
    void acumulaTotales_noPromediaLosConsumosDeCadaIntervalo() {
        List<Carga> cargas = List.of(
            new Carga(0, 30),     // linea base
            new Carga(100, 20),   // 100 km con 20 L -> 20 L/100km
            new Carga(110, 5));   // 10 km con 5 L  -> 50 L/100km

        // Total: 25 L en 110 km = 22.73 L/100km.
        Consumo c = calcularKm(cargas);
        assertEquals(new BigDecimal("22.73"), c.historico());

        // El promedio de los consumos de cada intervalo daria 35: un 54% mas.
        assertNotEquals(new BigDecimal("35.00"), c.historico());
    }

    @Test
    void unaCargaParcialSeCompensaConLaSiguiente() {
        // Mismo recorrido total y mismos litros totales, repartidos distinto
        // entre los dos intervalos: el historico no cambia. Es lo que hace al
        // metodo robusto frente a quien no llena el tanque.
        Consumo parejo = calcularKm(List.of(
            new Carga(0, 40), new Carga(100, 10), new Carga(200, 10)));
        Consumo desparejo = calcularKm(List.of(
            new Carga(0, 40), new Carga(100, 3), new Carga(200, 17)));

        assertEquals(parejo.historico(), desparejo.historico());
    }

    // ------------------------------------------------------------ ventana movil

    @Test
    void conMenosIntervalosQueLaVentana_recienteIgualaAlHistorico() {
        Consumo c = calcularKm(List.of(
            new Carga(0, 30), new Carga(100, 10), new Carga(200, 10)));
        assertEquals(c.historico(), c.reciente());
    }

    @Test
    void laVentanaIgnoraLasCargasViejas() {
        // Ventana de 2 intervalos. El vehiculo empeora al final: el historico se
        // queda a mitad de camino y el reciente muestra el deterioro.
        List<Carga> cargas = List.of(
            new Carga(0, 100),
            new Carga(100, 10),   // 10 L/100km
            new Carga(200, 10),   // 10 L/100km
            new Carga(300, 30),   // 30 L/100km
            new Carga(400, 30));  // 30 L/100km

        Consumo c = ConsumoCalculator.calcular(cargas, UnidadUso.KM, 2);

        // Historico: 80 L en 400 km = 20 L/100km.
        assertEquals(new BigDecimal("20.00"), c.historico());
        // Reciente (ultimos 2 intervalos): 60 L en 200 km = 30 L/100km.
        assertEquals(new BigDecimal("30.00"), c.reciente());
    }
}
