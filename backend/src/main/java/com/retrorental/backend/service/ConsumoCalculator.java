package com.retrorental.backend.service;

import com.retrorental.backend.model.enums.UnidadUso;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Consumo real de un vehiculo a partir de sus cargas.
 *
 * <h2>Como se calcula</h2>
 * Metodo de tanque lleno: los litros cargados en una parada reponen lo que se
 * gasto DESDE la parada anterior. O sea, para cada intervalo entre dos cargas:
 *
 * <pre>
 *   uso del intervalo = lectura de esta carga - lectura de la anterior
 *   litros del intervalo = litros cargados EN ESTA carga
 * </pre>
 *
 * <h2>Por que se suman los totales y no se promedian los consumos</h2>
 * Promediar el consumo de cada intervalo esta MAL: le da el mismo peso a un
 * intervalo de 100 km que a uno de 10. Con 100 km/20 L y 10 km/5 L, el promedio
 * de consumos da 35 L/100km y el consumo real es 25/110*100 = 22,7. Un 54% de
 * error. Por eso se acumulan litros y uso por separado y se dividen al final.
 *
 * <h2>La primera carga no cuenta</h2>
 * Solo fija la linea base. Sus litros no corresponden al uso previo salvo que
 * el vehiculo hubiera arrancado con el tanque exactamente lleno, que no se sabe.
 * Con n cargas hay n-1 intervalos utiles, asi que hacen falta al menos dos.
 *
 * <h2>Cargas parciales</h2>
 * Si alguien no llena el tanque, ese intervalo queda mal. Sumando totales el
 * error se autocorrige: lo que falta en un intervalo sobra en el siguiente. Con
 * un promedio de promedios, no.
 */
public final class ConsumoCalculator {

    private ConsumoCalculator() {
    }

    /**
     * Una carga: la lectura del contador y los litros cargados.
     *
     * `litros` es BigDecimal y no double a proposito. Esta clase ACUMULA litros
     * a lo largo de muchos intervalos (ver {@link #tasa}); con double primitivo
     * el error de representacion se sumaba vuelta a vuelta antes de que el total
     * se envolviera en BigDecimal, asi que envolverlo al final no servia de nada
     * (ver docs/BACKEND-AUDIT.md, DB-04).
     */
    public record Carga(int uso, BigDecimal litros) {
    }

    /**
     * @param historico sobre todas las cargas; null si no alcanzan los datos
     * @param reciente  sobre las ultimas cargas de la ventana; null idem
     */
    public record Consumo(BigDecimal historico, BigDecimal reciente) {
    }

    /**
     * @param cargas  ordenadas por lectura ascendente
     * @param unidad  del vehiculo: HORAS da L/h, KM da L/100km
     * @param ventana cuantos intervalos mira el consumo reciente
     */
    public static Consumo calcular(List<Carga> cargas, UnidadUso unidad, int ventana) {
        if (cargas == null || cargas.size() < 2) {
            return new Consumo(null, null);
        }

        int ultima = cargas.size() - 1;
        BigDecimal historico = tasa(cargas, 0, ultima, unidad);

        // Con menos intervalos que la ventana, "reciente" e "historico" son lo
        // mismo: se devuelve igual para que la lectura no dependa de cuantas
        // cargas hubo.
        int intervalos = cargas.size() - 1;
        int desde = intervalos <= ventana ? 0 : ultima - ventana;
        BigDecimal reciente = desde == 0 ? historico : tasa(cargas, desde, ultima, unidad);

        return new Consumo(historico, reciente);
    }

    /**
     * Consumo entre dos cargas del listado. Los litros del extremo `desde` NO se
     * cuentan: esa carga marca el inicio del tramo, no consumo dentro de el.
     */
    private static BigDecimal tasa(List<Carga> cargas, int desde, int hasta, UnidadUso unidad) {
        int uso = cargas.get(hasta).uso() - cargas.get(desde).uso();
        if (uso <= 0) {
            // Todas las lecturas iguales: no hay uso del cual derivar consumo.
            return null;
        }

        // Acumulacion EXACTA. Con double, cada suma agregaba su propio error de
        // representacion y el total derivaba proporcionalmente a la cantidad de
        // intervalos: justo el caso de un vehiculo con años de historial.
        BigDecimal litros = BigDecimal.ZERO;
        for (int i = desde + 1; i <= hasta; i++) {
            litros = litros.add(cargas.get(i).litros());
        }
        if (litros.signum() <= 0) {
            return null;
        }

        // En vehiculos de ruta el consumo se expresa cada 100 km; en maquinas,
        // por hora.
        BigDecimal factor = unidad == UnidadUso.HORAS ? BigDecimal.ONE : BigDecimal.valueOf(100);

        return litros
            .multiply(factor)
            .divide(BigDecimal.valueOf(uso), 2, RoundingMode.HALF_UP);
    }
}
