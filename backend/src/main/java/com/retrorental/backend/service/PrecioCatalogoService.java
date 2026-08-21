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
     * Resuelve el precio de una carga por su COMBUSTIBLE, sin un idPrecio previo
     * del catálogo. Lo usan la herramienta (cuyo combustible se elige carga por
     * carga) y el vehículo cuyo proveedor todavía no tiene precio de su
     * combustible fijo — ahí no hay nada que elegir en el formulario.
     *
     * - Si el proveedor ya tiene un vigente de ese combustible, se usa tal cual.
     * - Si NO lo tiene y el empleado tipeó un precio (precioManual): ese valor da
     *   de alta el vigente, previa validación contra la banda del catálogo (ver
     *   validarAltaInicial). Es el caso del proveedor recién dado de alta desde un
     *   ticket, que nace sin precios y antes dejaba al empleado sin poder cargar.
     * - Si NO lo tiene, no hay precio tipeado y es MEZCLA (nafta con aceite): no
     *   es un producto que vendan las estaciones, así que se crea copiando como
     *   valor inicial el vigente de NAFTA_SUPER del MISMO proveedor. El empleado
     *   ve ese valor y lo corrige al precio real de la mezcla (sin tope de margen,
     *   ver fueraDeMargen). Si el proveedor tampoco tiene ese NAFTA_SUPER, no hay
     *   de dónde copiar: error explícito, no se inventa un precio en cero.
     * - Para cualquier otro combustible sin vigente y sin precio tipeado: error
     *   explícito. Sin valor del empleado ni referencia de la cual copiar, no hay
     *   de dónde sacar el número.
     */
    public Precio resolvePorCombustible(Proveedor proveedor, TipoCombustible tipoCombustible,
                                        BigDecimal precioManual) {
        Precio vigente = vigenteDe(proveedor, tipoCombustible);
        if (vigente != null) {
            return vigente;
        }

        // El alta manual va ANTES del fallback de MEZCLA: si el empleado tipeó el
        // precio real, ese dato le gana a copiar el de la nafta como aproximación.
        if (precioManual != null) {
            validarAltaInicial(precioManual, tipoCombustible, "precioUnitario");
            return reemplazarVigente(proveedor, tipoCombustible, precioManual);
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

    /**
     * Si el precio propuesto para un ALTA se sale de lo que cobra el mercado.
     *
     * fueraDeMargen cubre la CORRECCION: hay un vigente del MISMO proveedor y se
     * mide contra él. Cuando el proveedor todavía no tiene vigente de ese
     * combustible no hay tal referencia, y hasta acá ese valor entraba al catálogo
     * sin control alguno — el caso de peor visibilidad y mayor daño, porque el
     * precio que se crea queda vigente para todos los que carguen después.
     *
     * La referencia acá es el propio catálogo: la franja que va del vigente más
     * barato al más caro de ESE combustible entre TODOS los proveedores, abierta
     * por margenMaximo en las dos puntas. Se mueve sola con la inflación, sin un
     * número mágico que alguien tenga que ir a actualizar.
     *
     * Las dos puntas importan: un techo solo deja pasar tipear 12 en vez de 1200.
     *
     * A diferencia de fueraDeMargen, la MEZCLA NO queda exenta. Allá se la exime
     * porque se la compara contra el vigente de NAFTA_SUPER, del que se aleja
     * legítimamente por el aceite; acá la referencia son OTRAS MEZCLAS del
     * catálogo, así que la comparación es válida y el tope rige.
     *
     * Si no hay NINGÚN vigente de ese combustible en todo el sistema no hay banda
     * que construir, y no se inventa un rango: se acepta. Mismo criterio que
     * fueraDeMargen con vigente en null.
     */
    public boolean fueraDeBandaDeAlta(BigDecimal propuesto, TipoCombustible tipoCombustible) {
        if (propuesto == null || tipoCombustible == null) {
            return false;
        }

        BigDecimal techo = precioRepository
            .findFirstByTipoCombustibleAndFechaHastaIsNullOrderByPrecioUnitarioDesc(tipoCombustible)
            .map(Precio::getPrecioUnitario)
            .orElse(null);
        BigDecimal piso = precioRepository
            .findFirstByTipoCombustibleAndFechaHastaIsNullOrderByPrecioUnitarioAsc(tipoCombustible)
            .map(Precio::getPrecioUnitario)
            .orElse(null);

        if (techo == null || piso == null) {
            return false;
        }

        return propuesto.compareTo(techo.add(margenMaximo)) > 0
            || propuesto.compareTo(piso.subtract(margenMaximo)) < 0;
    }

    /** Igual que fueraDeBandaDeAlta pero cortando la operación: la usa la carga manual. */
    public void validarAltaInicial(BigDecimal propuesto, TipoCombustible tipoCombustible,
                                   String campo) {
        if (fueraDeBandaDeAlta(propuesto, tipoCombustible)) {
            throw new ConflictException(
                ErrorCode.PRECIO_FUERA_DE_RANGO,
                "El precio ingresado (" + propuesto + ") se aleja mas de " + margenMaximo
                    + " de lo que cobran las demas estaciones por ese combustible."
                    + " Verificá el importe.",
                campo);
        }
    }
}
