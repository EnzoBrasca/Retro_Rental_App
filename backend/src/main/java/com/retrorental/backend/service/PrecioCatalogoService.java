package com.retrorental.backend.service;

import com.retrorental.backend.exception.ConflictException;
import com.retrorental.backend.exception.ErrorCode;
import com.retrorental.backend.exception.ResourceNotFoundException;
import com.retrorental.backend.model.Precio;
import com.retrorental.backend.model.Proveedor;
import com.retrorental.backend.model.enums.Servicio;
import com.retrorental.backend.model.enums.TipoCombustible;
import com.retrorental.backend.repository.PrecioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Catálogo de precios: resolución, reemplazo del vigente y control de margen.
 *
 * Extraído de TicketService, que había acumulado seis responsabilidades en 759
 * líneas (ver docs/BACKEND-AUDIT.md, SVC-01). Estas reglas no son "de tickets":
 * son del catálogo de precios, y las comparten tres caminos distintos — la carga
 * manual de un vehículo, la de una herramienta y la resolución desde el OCR.
 *
 * INVARIANTE que sostiene toda esta clase: hay a lo sumo UN precio vigente por
 * (proveedor, combustible), y es el que tiene fechaHasta en NULL. Un Precio
 * existente NUNCA se muta, porque hay tickets históricos que lo referencian:
 * corregir un precio siempre significa cerrar el vigente y crear una fila nueva.
 */
@Service
@RequiredArgsConstructor
public class PrecioCatalogoService {

    private final PrecioRepository precioRepository;

    // Cuanto puede alejarse del vigente un precio corregido a mano (o leido por
    // OCR) antes de considerarse un error de carga.
    @Value("${app.precio.margen-maximo:30}")
    private BigDecimal margenMaximo;

    /**
     * Resuelve el precio de una carga de VEHICULO, cuyo idPrecio ya vino elegido
     * del catálogo en el formulario.
     *
     * Valida coherencia precio↔proveedor: si el precio pertenece a una estación,
     * tiene que ser la MISMA que la del ticket. El precio es por (proveedor,
     * combustible), así que cargar una compra con el precio de OTRA estación
     * sería un dato inconsistente. Los precios legacy sin proveedor no se validan.
     */
    public Precio resolvePorId(Integer idPrecio, Proveedor proveedor) {
        Precio precio = precioRepository.findById(idPrecio)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.PRECIO_NOT_FOUND, "No existe el precio indicado", "idPrecio"));

        if (precio.getProveedor() != null
            && !precio.getProveedor().getId().equals(proveedor.getId())) {
            throw new ConflictException(
                ErrorCode.PRECIO_PROVEEDOR_MISMATCH,
                "El precio indicado corresponde a otra estación de servicio", "idPrecio");
        }
        return precio;
    }

    /**
     * Resuelve el precio de una carga de HERRAMIENTA, que no manda idPrecio (no
     * hay uno resuelto de antemano: el combustible se elige carga por carga) sino
     * el tipoCombustible elegido.
     *
     * - Si el proveedor ya tiene un vigente de ese combustible, se usa tal cual.
     * - Si NO lo tiene y es MEZCLA (nafta con aceite): no es un producto que
     *   vendan las estaciones, así que se crea copiando como valor inicial el
     *   vigente de NAFTA_SUPER del MISMO proveedor. El empleado ve ese valor y lo
     *   corrige al precio real de la mezcla (sin tope de margen, ver
     *   fueraDeMargen). Si el proveedor tampoco tiene ese NAFTA_SUPER, no hay de
     *   dónde copiar: error explícito, no se inventa un precio en cero.
     * - Para cualquier otro combustible sin vigente (ej. un bidón de GASOIL en un
     *   proveedor que no lo tiene cargado): error explícito. A diferencia de
     *   MEZCLA, ningún otro combustible tiene una referencia de la que copiar.
     */
    public Precio resolvePorCombustible(Proveedor proveedor, TipoCombustible tipoCombustible) {
        Precio vigente = vigenteDe(proveedor, tipoCombustible);
        if (vigente != null) {
            return vigente;
        }

        if (tipoCombustible == TipoCombustible.MEZCLA) {
            Precio base = precioRepository
                .findByProveedorAndTipoCombustibleAndFechaHastaIsNull(proveedor, TipoCombustible.NAFTA_SUPER)
                .orElseThrow(() -> new ResourceNotFoundException(
                    ErrorCode.PRECIO_BASE_MEZCLA_NOT_FOUND,
                    "El proveedor no tiene un precio vigente de nafta super para tomar como base de la mezcla",
                    "idProveedor"));
            return reemplazarVigente(proveedor, TipoCombustible.MEZCLA, base.getPrecioUnitario());
        }

        throw new ResourceNotFoundException(
            ErrorCode.PRECIO_NOT_FOUND_PARA_COMBUSTIBLE,
            "El proveedor no tiene un precio vigente para ese combustible", "idProveedor");
    }

    /** El precio vigente de (proveedor, combustible), o null si no hay. */
    public Precio vigenteDe(Proveedor proveedor, TipoCombustible tipoCombustible) {
        return precioRepository
            .findByProveedorAndTipoCombustibleAndFechaHastaIsNull(proveedor, tipoCombustible)
            .orElse(null);
    }

    /**
     * Retira el precio vigente de (proveedor, combustible) y deja uno nuevo.
     *
     * Conserva la historia: el anterior queda con fechaHasta = hoy en vez de
     * borrarse, así los tickets viejos siguen apuntando al precio que realmente
     * se pagó. Lo comparten la corrección manual del empleado y la ruta del OCR.
     */
    public Precio reemplazarVigente(Proveedor proveedor, TipoCombustible tipoCombustible,
                                    BigDecimal valor) {
        LocalDate hoy = LocalDate.now();
        precioRepository
            .findByProveedorAndTipoCombustibleAndFechaHastaIsNull(proveedor, tipoCombustible)
            .ifPresent(vigente -> vigente.setFechaHasta(hoy));

        Precio nuevo = new Precio();
        nuevo.setPrecioUnitario(valor);
        nuevo.setServicio(Servicio.COMBUSTIBLE);
        nuevo.setTipoCombustible(tipoCombustible);
        nuevo.setProveedor(proveedor);
        nuevo.setFechaDesde(hoy);
        nuevo.setFechaHasta(null);
        return precioRepository.save(nuevo);
    }

    /**
     * Si el precio propuesto se aleja del vigente más de lo tolerado.
     *
     * La MEZCLA (nafta con aceite, motosierra) queda SIN TOPE: cuando el
     * proveedor no tiene un vigente propio, se crea tomando como base el vigente
     * de NAFTA_SUPER de ese proveedor (ver resolvePorCombustible), y el usuario
     * lo corrige después al precio real de la mezcla, que lleva aceite y por eso
     * se aleja legítimamente del de la nafta pura. Aplicarle el mismo margen que
     * al resto trabaría correcciones válidas.
     */
    public boolean fueraDeMargen(BigDecimal propuesto, BigDecimal vigente,
                                 TipoCombustible tipoCombustible) {
        if (propuesto == null || vigente == null) {
            return false;
        }
        if (tipoCombustible == TipoCombustible.MEZCLA) {
            return false;
        }
        return propuesto.subtract(vigente).abs().compareTo(margenMaximo) > 0;
    }

    /** Igual que fueraDeMargen pero cortando la operación: la usa la carga manual. */
    public void validarMargen(BigDecimal propuesto, BigDecimal vigente,
                              TipoCombustible tipoCombustible, String campo) {
        if (fueraDeMargen(propuesto, vigente, tipoCombustible)) {
            throw new ConflictException(
                ErrorCode.PRECIO_FUERA_DE_RANGO,
                "El precio ingresado (" + propuesto + ") se aleja mas de " + margenMaximo
                    + " del precio vigente (" + vigente + "). Verificá el importe.",
                campo);
        }
    }
}
