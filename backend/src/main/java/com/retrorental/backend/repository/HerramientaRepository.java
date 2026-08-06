package com.retrorental.backend.repository;

import com.retrorental.backend.model.Herramienta;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HerramientaRepository extends JpaRepository<Herramienta, Integer> {
    // Solo las activas: alimenta GET /herramientas (lectura del empleado).
    List<Herramienta> findByFechaBajaIsNull();
}
