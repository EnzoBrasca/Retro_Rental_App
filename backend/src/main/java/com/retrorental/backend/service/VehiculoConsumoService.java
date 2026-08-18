package com.retrorental.backend.service;

import com.retrorental.backend.model.Vehiculo;
import com.retrorental.backend.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Recálculo del consumo y de la lectura del contador de un vehículo a partir de
 * sus cargas vigentes.
 *
 * Extraído de TicketService (ver docs/BACKEND-AUDIT.md, SVC-01). Lo llaman los
 * dos caminos que cambian el conjunto de cargas de un vehículo: el alta de un
 * ticket y la anulación.
 *
 * NINGUNO de los dos métodos guarda el vehículo: mutan la entidad y dejan que el
 * llamador decida cuándo persistir. Es a propósito — el alta cambia además la
 * lectura y el operario, y así el vehículo se guarda UNA sola vez con todo junto
 * en vez de tres veces.
 *
 * Las cargas de HERRAMIENTA nunca participan de estos cálculos: una herramienta
 * no tiene contador ni consumo promedio.
 */
@Service
@RequiredArgsConstructor
public class VehiculoConsumoService {

    private final TicketRepository ticketRepository;

    // Cuantos intervalos mira el consumo "reciente" del vehiculo.
    @Value("${app.consumo.ventana-cargas:10}")
    private int ventanaConsumo;

    /**
     * Recalcula el consumo del vehículo a partir de sus cargas.
     *
     * Se hace en cada alta de ticket y no al leer para que el listado de la
     * flota no dispare una consulta por vehículo. El costo es una consulta por
     * carga, que es la operación poco frecuente de las dos.
     *
     * Si no hay datos suficientes (hacen falta dos cargas con lectura), se vuelve
     * a consumoInicial: la estimación que cargó el admin en el alta.
     *
     * Ese fallback existe por la ANULACIÓN. Mientras los tickets solo se
     * agregaban, alcanzaba con no tocar consumoPromedio cuando el cálculo no
     * daba: el valor que había era justamente la estimación del alta. Al poder
     * anular, el vehículo puede RETROCEDER a menos de dos cargas, y entonces "no
     * tocar" dejaría un consumo calculado a partir de cargas que ya no existen.
     * Un número que sobrevive a la evidencia que lo sustentaba.
     *
     * Si consumoInicial es null (vehículos anteriores a V7, cuya estimación
     * original ya se había perdido) no hay a qué volver: se deja lo que hay, que
     * es lo único que se puede hacer sin inventar un dato.
     */
    public void recalcularConsumo(Vehiculo vehiculo) {
        List<ConsumoCalculator.Carga> cargas = cargasConLectura(vehiculo)
            .stream()
            .map(c -> new ConsumoCalculator.Carga(c.getUsoAcumulado(), c.getLitros()))
            .toList();

        ConsumoCalculator.Consumo consumo = ConsumoCalculator.calcular(
            cargas, vehiculo.getTipoVehiculo().unidadUso(), ventanaConsumo);

        if (consumo.historico() != null) {
            vehiculo.setConsumoPromedio(consumo.historico());
        } else if (vehiculo.getConsumoInicial() != null) {
            vehiculo.setConsumoPromedio(vehiculo.getConsumoInicial());
        }
        vehiculo.setConsumoReciente(consumo.reciente());
    }

    /**
     * Recalcula la lectura del contador del vehículo desde sus cargas vigentes.
     *
     * Es lo que hace útil a la anulación. El motivo más común para anular un
     * ticket es un error de tipeo en la lectura (99999 en vez de 9999), y esa
     * lectura equivocada quedó copiada en vehiculo.usoAcumulado. Sin revertirla,
     * toda carga futura del vehículo se rechazaría con USO_ACUMULADO_RETROCEDE
     * contra un número que ya nadie puede justificar: la función que el admin usa
     * para arreglar el error lo dejaría sin poder arreglarlo.
     *
     * Sin cargas vigentes no se toca: la lectura previa a todos los tickets no se
     * guarda en ningún lado. El admin la corrige a mano desde el ABM.
     */
    public void recalcularUsoAcumulado(Vehiculo vehiculo) {
        cargasConLectura(vehiculo)
            .stream()
            .map(TicketRepository.CargaParaConsumo::getUsoAcumulado)
            .max(Integer::compareTo)
            .ifPresent(vehiculo::setUsoAcumulado);
    }

    // Cargas del vehiculo que sirven para los dos calculos: vigentes y con
    // lectura del contador. Las anteriores a esa feature quedan afuera porque no
    // aportan intervalo.
    //
    // Devuelve una PROYECCION de (usoAcumulado, litros), no entidades: es lo que
    // permite que la consulta se responda entera desde el indice cubridor de
    // V11 sin tocar la tabla. Ver TicketRepository.findCargasParaConsumo.
    private List<TicketRepository.CargaParaConsumo> cargasConLectura(Vehiculo vehiculo) {
        return ticketRepository.findCargasParaConsumo(vehiculo.getId());
    }
}
