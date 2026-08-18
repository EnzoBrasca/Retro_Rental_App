package com.retrorental.backend.repository;

import com.retrorental.backend.model.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
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

    // Cargas de un vehiculo que sirven para calcular consumo: las que tienen
    // lectura del contador, en orden de lectura. Las anteriores a esa feature
    // quedan afuera porque no aportan intervalo.
    List<Ticket> findByVehiculoIdAndUsoAcumuladoIsNotNullAndFechaAnulacionIsNullOrderByUsoAcumuladoAsc(
        Integer vehiculoId);

    List<Ticket> findByPersonaIdAndFechaAnulacionIsNull(Integer personaId);
    List<Ticket> findByProveedorIdAndFechaAnulacionIsNull(Integer proveedorId);
    List<Ticket> findByFechaCargaBetweenAndFechaAnulacionIsNull(
        LocalDateTime desde, LocalDateTime hasta);
    // El listado con filtros opcionales para el admin usa Specification
    // (JpaSpecificationExecutor.findAll) — ver TicketService.listForAdmin.

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
        + "AND t.fechaAnulacion IS NULL")
    List<Ticket> findForStats(@Param("desde") LocalDateTime desde,
                              @Param("hasta") LocalDateTime hasta);

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
