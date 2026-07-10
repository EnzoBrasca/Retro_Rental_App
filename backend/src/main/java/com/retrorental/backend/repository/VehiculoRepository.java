package com.retrorental.backend.repository;

import com.retrorental.backend.model.Vehiculo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface VehiculoRepository extends JpaRepository<Vehiculo, Integer> {
    Optional<Vehiculo> findByPatente(String patente);
    boolean existsByPatente(String patente);

    // El listado completo trae el operario en la MISMA query (LEFT JOIN): la card
    // del empleado muestra "en uso por X", y sin el fetch cada vehiculo dispararia
    // una consulta extra al leer operario (LAZY) -> N+1.
    @Override
    @EntityGraph(attributePaths = "operario")
    List<Vehiculo> findAll();
}