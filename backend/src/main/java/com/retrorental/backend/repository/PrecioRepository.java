package com.retrorental.backend.repository;

import com.retrorental.backend.model.Precio;
import com.retrorental.backend.model.Proveedor;
import com.retrorental.backend.model.enums.Servicio;
import com.retrorental.backend.model.enums.TipoCombustible;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PrecioRepository extends JpaRepository<Precio, Integer> {
    List<Precio> findByServicio(Servicio servicio);
    Optional<Precio> findByServicioAndFechaHastaIsNull(Servicio servicio);

    // Precio vigente de un producto concreto. A diferencia del anterior, este es
    // único: el catálogo tiene UN vigente por tipo de combustible, no por servicio.
    Optional<Precio> findByServicioAndTipoCombustibleAndFechaHastaIsNull(
        Servicio servicio, TipoCombustible tipoCombustible);

    // Precio vigente de un producto EN UN PROVEEDOR concreto. Es la consulta única
    // en el modelo por proveedor: cada (proveedor, combustible) tiene un vigente.
    Optional<Precio> findByProveedorAndTipoCombustibleAndFechaHastaIsNull(
        Proveedor proveedor, TipoCombustible tipoCombustible);
}
