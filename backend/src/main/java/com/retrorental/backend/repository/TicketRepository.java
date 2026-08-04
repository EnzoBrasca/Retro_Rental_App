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
    // Cargas de un vehiculo que sirven para calcular consumo: las que tienen
    // lectura del contador, en orden de lectura. Las anteriores a esa feature
    // quedan afuera porque no aportan intervalo.
    List<Ticket> findByVehiculoIdAndUsoAcumuladoIsNotNullOrderByUsoAcumuladoAsc(Integer vehiculoId);

    List<Ticket> findByPersonaId(Integer personaId);
    // Historial de la persona: sus tickets, más recientes primero.
    List<Ticket> findByPersonaIdOrderByFechaCargaDesc(Integer personaId);
    List<Ticket> findByProveedorId(Integer proveedorId);
    List<Ticket> findByFechaCargaBetween(LocalDateTime desde, LocalDateTime hasta);
    // El listado con filtros opcionales para el admin usa Specification
    // (JpaSpecificationExecutor.findAll) — ver TicketService.listForAdmin.

    /**
     * Tickets de un período [desde, hasta) para el cálculo de estadísticas.
     * Límite superior EXCLUSIVO. Trae precio, vehiculo, persona y proveedor
     * con JOIN FETCH para evitar el N+1 al agregar el gasto y los desgloses
     * por vehiculo/empleado/proveedor en memoria.
     */
    @Query("SELECT t FROM Ticket t "
        + "JOIN FETCH t.precio "
        + "JOIN FETCH t.vehiculo "
        + "JOIN FETCH t.persona "
        + "JOIN FETCH t.proveedor "
        + "WHERE t.fechaCarga >= :desde AND t.fechaCarga < :hasta")
    List<Ticket> findForStats(@Param("desde") LocalDateTime desde,
                              @Param("hasta") LocalDateTime hasta);
}
