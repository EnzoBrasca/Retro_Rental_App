package com.retrorental.backend.repository;

import com.retrorental.backend.model.Ticket;
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
    // Historial de la persona: sus tickets vigentes, más recientes primero.
    List<Ticket> findByPersonaIdAndFechaAnulacionIsNullOrderByFechaCargaDesc(Integer personaId);
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
}
