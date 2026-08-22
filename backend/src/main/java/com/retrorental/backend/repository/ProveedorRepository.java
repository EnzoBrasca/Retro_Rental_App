package com.retrorental.backend.repository;

import com.retrorental.backend.model.Proveedor;
import com.retrorental.backend.model.enums.Servicio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProveedorRepository extends JpaRepository<Proveedor, Integer> {
    // Idempotencia del alta automatica desde el OCR: si ya hay un proveedor con
    // ese CUIT se reutiliza en vez de duplicarlo (ver CatalogoOcrResolver).
    Optional<Proveedor> findByCuit(String cuit);

    List<Proveedor> findByServicio(Servicio servicio);

    // Las estaciones REALES, sin el proveedor generico. Lo usa el seeder de dev:
    // el generico lo crea la migracion V14, asi que contar/recorrer todas las
    // filas le haria creer que el catalogo ya esta sembrado.
    long countByGenericoFalse();

    List<Proveedor> findByGenericoFalse();
}
