package com.retrorental.backend.repository;

import com.retrorental.backend.model.Vehiculo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface VehiculoRepository extends JpaRepository<Vehiculo, Integer> {

    Optional<Vehiculo> findByIdentificador(String identificador);

    boolean existsByIdentificador(String identificador);

    /**
     * Libera de una sola vez todos los vehiculos asignados a un empleado. Lo usa
     * la baja de empleado.
     *
     * Reemplaza un bucle que hacia un save() por vehiculo (ver
     * docs/BACKEND-AUDIT.md, DB-08). Los save() eran redundantes —el dirty
     * checking ya persistia el cambio— pero el flush igual emitia un UPDATE por
     * fila. Con esto es una sola sentencia.
     *
     * clearAutomatically + flushAutomatically porque una query de modificacion
     * pasa POR ENCIMA del contexto de persistencia: sin eso, las entidades
     * Vehiculo que ya estuvieran cargadas en la sesion seguirian mostrando el
     * operario viejo despues de esta llamada.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Vehiculo v SET v.operario = NULL WHERE v.operario.id = :empleadoId")
    int desasignarTodosDe(@Param("empleadoId") Integer empleadoId);

    // El listado completo trae el operario en la MISMA query (LEFT JOIN): la card
    // del empleado muestra "en uso por X", y sin el fetch cada vehiculo dispararia
    // una consulta extra al leer operario (LAZY) -> N+1.
    @Override
    @EntityGraph(attributePaths = "operario")
    List<Vehiculo> findAll();
}