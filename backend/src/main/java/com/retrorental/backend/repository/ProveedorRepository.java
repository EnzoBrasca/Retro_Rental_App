package com.retrorental.backend.repository;

import com.retrorental.backend.model.Proveedor;
import com.retrorental.backend.model.enums.Servicio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProveedorRepository extends JpaRepository<Proveedor, Integer> {
    Optional<Proveedor> findByCuit(String cuit);
    boolean existsByCuit(String cuit);
    List<Proveedor> findByServicio(Servicio servicio);
}
