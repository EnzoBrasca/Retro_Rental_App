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
    List<Ticket> findByEmpleadoId(Integer empleadoId);
    // Historial del empleado: sus tickets, más recientes primero.
    List<Ticket> findByEmpleadoIdOrderByFechaCargaDesc(Integer empleadoId);
    List<Ticket> findByProveedorId(Integer proveedorId);
    List<Ticket> findByFechaCargaBetween(LocalDateTime desde, LocalDateTime hasta);
    // El listado con filtros opcionales para el admin usa Specification
    // (JpaSpecificationExecutor.findAll) — ver TicketService.listForAdmin.

    /**
     * Tickets de un período [desde, hasta) para el cálculo de estadísticas.
     * Límite superior EXCLUSIVO. Trae precio y vehiculo con JOIN FETCH para
     * evitar el N+1 al agregar el gasto y el desglose por vehiculo en memoria.
     */
    @Query("SELECT t FROM Ticket t "
        + "JOIN FETCH t.precio "
        + "JOIN FETCH t.vehiculo "
        + "WHERE t.fechaCarga >= :desde AND t.fechaCarga < :hasta")
    List<Ticket> findForStats(@Param("desde") LocalDateTime desde,
                              @Param("hasta") LocalDateTime hasta);
}
