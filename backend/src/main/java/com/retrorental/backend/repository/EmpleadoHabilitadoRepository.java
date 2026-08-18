package com.retrorental.backend.repository;

import com.retrorental.backend.model.EmpleadoHabilitado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmpleadoHabilitadoRepository extends JpaRepository<EmpleadoHabilitado, Integer> {

    Optional<EmpleadoHabilitado> findByDocumento(String documento);

    boolean existsByDocumento(String documento);

    /**
     * Los habilitados cuyo documento esta en la lista. Lo usa el alta masiva
     * para resolver en UNA consulta cuales ya existen, en vez de un
     * existsByDocumento por elemento (ver docs/BACKEND-AUDIT.md, DB-07).
     */
    List<EmpleadoHabilitado> findByDocumentoIn(List<String> documentos);

    /**
     * Padrón completo para el ABM del administrador, altas más recientes
     * primero.
     *
     * LEFT JOIN FETCH sobre persona porque el mapeo a HabilitadoResponse lee
     * `persona.getUsername()` de cada fila ya usada. Sin el fetch, eso es un
     * SELECT extra por documento consumido del padrón (ver
     * docs/BACKEND-AUDIT.md, DB-05).
     *
     * LEFT y no INNER: las filas del padrón que todavía NO se usaron tienen
     * `persona` en NULL y son justamente las que el admin más necesita ver.
     * Un INNER JOIN las borraría del listado.
     */
    @Query("SELECT h FROM EmpleadoHabilitado h "
        + "LEFT JOIN FETCH h.persona "
        + "ORDER BY h.fechaAlta DESC")
    List<EmpleadoHabilitado> findAllConPersona();
}
