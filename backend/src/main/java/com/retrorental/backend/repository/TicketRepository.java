package com.retrorental.backend.repository;

import com.retrorental.backend.model.Ticket;
import com.retrorental.backend.model.enums.TipoVehiculo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TicketRepository
        extends JpaRepository<Ticket, Integer>, JpaSpecificationExecutor<Ticket> {
    // -----------------------------------------------------------------------
    // TODAS las consultas de lectura filtran fechaAnulacion IS NULL.
    //
    // Un ticket anulado sigue existiendo como fila (es un registro contable),
    // pero no debe participar de NINGUN calculo ni listado: si se colara,
    // seguiria sumando gasto en la analitica y sosteniendo el consumo de un
    // vehiculo con una carga que el admin dio de baja.
    //
    // La unica consulta que ve anulados es el listado del admin, y solo cuando
    // los pide explicitamente (ver TicketService.listForAdmin).
    // -----------------------------------------------------------------------

    /**
     * Cargas de un vehiculo que sirven para calcular consumo: las que tienen
     * lectura del contador, en orden de lectura. Las anteriores a esa feature
     * quedan afuera porque no aportan intervalo.
     *
     * Devuelve una PROYECCION de dos columnas, no entidades `Ticket`. El motivo
     * es de rendimiento y esta medido (ver docs/BACKEND-AUDIT.md, DB-03):
     * el recalculo solo necesita (usoAcumulado, litros), y mientras se trajera
     * la entidad completa Postgres tenia que ir al heap si o si, sin importar
     * que indices hubiera. Con la proyeccion mas el indice cubridor de V11 la
     * consulta se responde entera desde el indice.
     *
     * Sobre 200.000 tickets: de 2.074 buffers y 1,53 ms a 16 buffers y 0,19 ms,
     * en el camino que corre en CADA carga de combustible registrada.
     *
     * Si alguien necesita el Ticket entero para otra cosa, que agregue otra
     * consulta: cambiar esta de vuelta a entidades revierte el arreglo en
     * silencio y deja el indice de V11 sin usar.
     */
    @Query("SELECT t.usoAcumulado AS usoAcumulado, t.litros AS litros "
        + "FROM Ticket t "
        + "WHERE t.vehiculo.id = :vehiculoId "
        + "AND t.usoAcumulado IS NOT NULL "
        + "AND t.fechaAnulacion IS NULL "
        + "ORDER BY t.usoAcumulado ASC")
    List<CargaParaConsumo> findCargasParaConsumo(@Param("vehiculoId") Integer vehiculoId);

    /**
     * Las dos unicas columnas que el recalculo de consumo necesita de un ticket.
     * Proyeccion por interfaz: Spring Data genera un SELECT de esos dos campos
     * en vez de hidratar la entidad.
     */
    interface CargaParaConsumo {
        Integer getUsoAcumulado();

        BigDecimal getLitros();
    }

    /**
     * Misma idea que findCargasParaConsumo, pero para el consumo DEL PERIODO que
     * muestra el panel de estadisticas (StatsService.consumoDelPeriodo).
     *
     * Necesita dos campos mas: `fechaCarga` para partir las cargas en previas,
     * dentro y posteriores al rango, y `tipoVehiculo` para saber la unidad
     * (L/h en maquinas, L/100km en el resto).
     *
     * `tipoVehiculo` viaja EN la proyeccion, resuelto con un join. Antes se
     * obtenia con `cargas.get(0).getVehiculo().getTipoVehiculo()`, que disparaba
     * un SELECT lazy extra (ver docs/BACKEND-AUDIT.md, SVC-06). Ahora sale de la
     * misma consulta.
     *
     * Va aparte de findCargasParaConsumo a proposito: el join a vehiculos hace
     * que esta consulta NO pueda resolverse solo desde el indice cubridor de
     * V11. Meterle estos campos a aquella arruinaria el Index Only Scan del
     * camino caliente para favorecer a un camino de lectura poco frecuente.
     */
    @Query("SELECT t.usoAcumulado AS usoAcumulado, t.litros AS litros, "
        + "t.fechaCarga AS fechaCarga, t.vehiculo.tipoVehiculo AS tipoVehiculo "
        + "FROM Ticket t "
        + "WHERE t.vehiculo.id = :vehiculoId "
        + "AND t.usoAcumulado IS NOT NULL "
        + "AND t.fechaAnulacion IS NULL "
        + "ORDER BY t.usoAcumulado ASC")
    List<CargaParaStats> findCargasParaStats(@Param("vehiculoId") Integer vehiculoId);

    interface CargaParaStats {
        Integer getUsoAcumulado();

        BigDecimal getLitros();

        LocalDateTime getFechaCarga();

        TipoVehiculo getTipoVehiculo();
    }

    // El listado con filtros opcionales para el admin usa Specification
    // (JpaSpecificationExecutor.findAll) — ver TicketService.listForAdmin.
    //
    // Acá había tres consultas derivadas sin un solo llamador (por persona, por
    // proveedor y por rango de fechas). Se borraron en la fase 4 (ver
    // docs/BACKEND-AUDIT.md, DB-10). La de proveedor parecía escrita para el
    // filtro del listado del admin, que terminó resolviéndose con un predicado
    // en la Specification; ese camino ya tiene su índice desde V10.

    /**
     * Tickets VIGENTES de un período [desde, hasta) para el cálculo de
     * estadísticas. Límite superior EXCLUSIVO. Trae precio, vehiculo, herramienta,
     * persona y proveedor con JOIN FETCH para evitar el N+1 al agregar el gasto y
     * los desgloses por vehiculo/empleado/proveedor en memoria.
     *
     * vehiculo y herramienta van con LEFT JOIN FETCH (no JOIN a secas): un
     * ticket tiene exactamente uno de los dos, así que un INNER JOIN sobre
     * cualquiera de las dos EXCLUIRÍA de las estadísticas los tickets del otro
     * origen (los de herramienta desaparecerían con INNER JOIN a vehiculo, y
     * viceversa).
     *
     * El filtro de anulados es lo que hace que anular un ticket corrija SOLO el
     * gasto de la analítica: no hay ningún total guardado, todo se agrega al
     * vuelo desde acá.
     */
    @Query("SELECT t FROM Ticket t "
        + "JOIN FETCH t.precio "
        + "LEFT JOIN FETCH t.vehiculo "
        + "LEFT JOIN FETCH t.herramienta "
        + "JOIN FETCH t.persona "
        + "JOIN FETCH t.proveedor "
        + "WHERE t.fechaCarga >= :desde AND t.fechaCarga < :hasta "
        + "AND t.fechaAnulacion IS NULL "
        // Filtros OPCIONALES, aplicados en la BASE y no en memoria (ver
        // docs/BACKEND-AUDIT.md, SVC-02). Antes esta consulta traia e hidrataba
        // TODOS los tickets del rango, con sus cinco asociaciones, y recien
        // despues descartaba en Java los que no correspondian al vehiculo o al
        // empleado elegido.
        //
        // El filtro de empleados va con un BOOLEANO explicito y no con
        // ":empleadoIds IS NULL": si la lista viniera null, el IN igual se
        // bindea y el comportamiento depende de como Hibernate resuelva una
        // coleccion vacia. Con el flag no hay ambiguedad, a costa de que el
        // llamador tenga que pasar una lista no nula (ver StatsService).
        + "AND (:vehiculoId IS NULL OR t.vehiculo.id = :vehiculoId) "
        + "AND (:filtrarPorEmpleado = false OR t.persona.id IN :empleadoIds)")
    List<Ticket> findForStats(@Param("desde") LocalDateTime desde,
                              @Param("hasta") LocalDateTime hasta,
                              @Param("vehiculoId") Integer vehiculoId,
                              @Param("filtrarPorEmpleado") boolean filtrarPorEmpleado,
                              @Param("empleadoIds") List<Integer> empleadoIds);

    /**
     * Historial de la persona: sus tickets VIGENTES, más recientes primero.
     * Alimenta la pestaña "Historial" de la app del empleado.
     *
     * Trae precio, proveedor, persona, vehiculo y herramienta con JOIN FETCH por
     * el mismo motivo que {@link #findForStats}: sin esto, el mapeo a
     * TicketResponse dereferencia cinco relaciones LAZY por ticket, o sea hasta
     * CINCO SELECTs extra por fila. Sobre un historial que crece con cada carga
     * y no se borra nunca, eso escala pésimo (ver docs/BACKEND-AUDIT.md, DB-01).
     *
     * vehiculo y herramienta van con LEFT JOIN FETCH, no con JOIN a secas: un
     * ticket tiene exactamente uno de los dos, así que un INNER JOIN sobre
     * cualquiera de ellos haría desaparecer del historial los tickets del otro
     * origen. Mismo razonamiento que en findForStats.
     *
     * `anuladoPor` NO se trae a propósito: esta consulta filtra vigentes, así
     * que esa FK siempre viene NULL y Hibernate la resuelve sin ir a la base
     * (el valor está en la propia fila del ticket).
     *
     * Todas las relaciones traídas son ManyToOne (de valor único), no
     * colecciones, y por eso el fetch join es compatible con la paginación:
     * Hibernate pagina en SQL. Con una colección tendría que traer todo y
     * paginar en memoria (el temido HHH000104).
     *
     * La countQuery va explícita y SIN los fetch joins: contar no necesita las
     * asociaciones, y un fetch dentro de un count rompe. Mismo criterio que la
     * Specification de listForAdmin.
     *
     * El índice que soporta esta consulta es `tickets_persona_vigentes_idx`
     * (V10). El de V7 no sirve acá: indexa solo las filas anuladas. Medido
     * sobre 200.000 tickets, paginado usa ese índice con Index Scan (13
     * buffers, 0,08 ms); sin paginar el planner lo ignora y ordena en memoria
     * (2.074 buffers, 3,85 ms). O sea: el índice y la paginación son un
     * paquete, no dos mejoras independientes.
     */
    @Query(value = "SELECT t FROM Ticket t "
        + "JOIN FETCH t.precio "
        + "JOIN FETCH t.proveedor "
        + "JOIN FETCH t.persona "
        + "LEFT JOIN FETCH t.vehiculo "
        + "LEFT JOIN FETCH t.herramienta "
        + "WHERE t.persona.id = :personaId AND t.fechaAnulacion IS NULL "
        + "ORDER BY t.fechaCarga DESC",
        countQuery = "SELECT count(t) FROM Ticket t "
        + "WHERE t.persona.id = :personaId AND t.fechaAnulacion IS NULL")
    Page<Ticket> findVigentesDePersona(@Param("personaId") Integer personaId, Pageable pageable);
}
