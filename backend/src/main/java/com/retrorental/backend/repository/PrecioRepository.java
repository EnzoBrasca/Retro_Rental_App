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

    // Acá había dos consultas derivadas por `servicio` sin un solo llamador
    // (una por servicio y otra por servicio + combustible). Se borraron en la
    // fase 4 (ver docs/BACKEND-AUDIT.md, DB-10). Quedaron obsoletas cuando el
    // catálogo pasó a ser por (proveedor, combustible): buscar el vigente "del
    // servicio" ya no identifica un precio único.

    // Precio vigente de un producto EN UN PROVEEDOR concreto. Es la consulta única
    // en el modelo por proveedor: cada (proveedor, combustible) tiene un vigente.
    Optional<Precio> findByProveedorAndTipoCombustibleAndFechaHastaIsNull(
        Proveedor proveedor, TipoCombustible tipoCombustible);

    /**
     * Todos los precios VIGENTES, sin importar proveedor ni producto. Alimenta
     * el selector del formulario de carga.
     *
     * Reemplaza a un `findAll()` + filtro en memoria (ver docs/BACKEND-AUDIT.md,
     * DB-06). Esa version cargaba el HISTORIAL COMPLETO de precios, que solo
     * crece: `reemplazarVigente` nunca borra filas, las cierra con fechaHasta.
     * Cada apertura del formulario pagaba todos los cambios de precio de la
     * historia del sistema para quedarse con un puñado de filas.
     */
    List<Precio> findByFechaHastaIsNull();
}
