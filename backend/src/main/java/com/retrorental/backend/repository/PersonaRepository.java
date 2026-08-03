package com.retrorental.backend.repository;

import com.retrorental.backend.model.Empleado;
import com.retrorental.backend.model.Persona;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PersonaRepository extends JpaRepository<Persona, Integer> {
    Optional<Persona> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByDocumento(String documento);
    List<Empleado> findAllByOrderByFechaAltaDesc();
}