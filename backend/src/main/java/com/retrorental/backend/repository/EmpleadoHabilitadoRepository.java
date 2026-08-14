package com.retrorental.backend.repository;

import com.retrorental.backend.model.EmpleadoHabilitado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmpleadoHabilitadoRepository extends JpaRepository<EmpleadoHabilitado, Integer> {

    Optional<EmpleadoHabilitado> findByDocumento(String documento);

    boolean existsByDocumento(String documento);

    List<EmpleadoHabilitado> findAllByOrderByFechaAltaDesc();
}
