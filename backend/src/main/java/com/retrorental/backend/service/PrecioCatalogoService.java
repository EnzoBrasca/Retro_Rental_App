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
     * - Si el proveedor es GENERICO no hay herencia posible: ver altaSiempre.
     * - Si el proveedor ya tiene un vigente de ese combustible, se usa tal cual.
     * - Si NO lo tiene y el empleado tipeó un precio (precioManual): ese valor da
     *   de alta el vigente, previa validación contra la banda del catálogo (ver
     *   validarAltaInicial). Es el caso del proveedor recién dado de alta desde un
     *   ticket, que nace sin precios y antes dejaba al empleado sin poder cargar.
     * - Para cualquier combustible sin vigente y sin precio tipeado: error
     *   explícito. Sin valor del empleado no hay de dónde sacar el número.
     *
     * La MEZCLA NO pasa por acá: su precio se calcula, no se elige (ver
     * resolveMezcla). Y no puede llegar por el camino del vehículo, porque
     * ningún vehículo carga mezcla — lo garantiza el CHECK de
     * vehiculos.tipo_combustible.
     */
    public Precio resolvePorCombustible(Proveedor proveedor, TipoCombustible tipoCombustible,
                                        BigDecimal precioManual) {
        if (proveedor.isGenerico()) {
            return altaSiempre(proveedor, tipoCombustible, precioManual);
        }

        Precio vigente = vigenteDe(proveedor, tipoCombustible);
        if (vigente != null) {
            return vigente;
        }

        if (precioManual != null) {
            validarAltaInicial(precioManual, tipoCombustible, "precioUnitario");
            return reemplazarVigente(proveedor, tipoCombustible, precioManual);
        }

        throw new ResourceNotFoundException(
            ErrorCode.PRECIO_NOT_FOUND_PARA_COMBUSTIBLE,
            "El proveedor no tiene un precio vigente para ese combustible", "idProveedor");
    }

    /**
     * Resuelve el precio de una carga de MEZCLA (nafta con aceite, 2 tiempos).
     *
     * La mezcla es el único producto cuyo precio se CALCULA en vez de elegirse,
     * y por eso tiene su propio camino. Nadie la vende en un surtidor: no está
     * en el ticket ni en ninguna etiqueta. Antes se le pedía ese número al
     * empleado ofreciéndole como valor inicial el de NAFTA_SUPER — que es la
     * mezcla SIN el aceite, o sea el error entero. Ahora sale de datos que sí se
     * pueden observar: el precio del aceite (está en la botella) y el de la
     * nafta (ya vive en el catálogo).
     *
     * Orden de resolución:
     *
     * 1. Si viene precioAceite, actualiza el vigente de (proveedor, ACEITE) para
     *    que la próxima carga en esa estación ya no lo pida.
     * 2. Con nafta y aceite del proveedor, calcula. Si el resultado coincide con
     *    la mezcla vigente, la reutiliza en vez de abrir una fila nueva.
     * 3. Si no se puede calcular (falta la nafta o el aceite) pero hay una mezcla
     *    vigente, se usa esa: es el mejor dato disponible.
     * 4. Si tampoco hay vigente pero el empleado tipeó un precio, ese da de alta
     *    (misma vía que cualquier otro combustible).
     * 5. Si no hay ninguna de las tres cosas, error explícito.
     *
     * Los pasos 3 y 4 existen para que esto NUNCA sea un callejón sin salida: el
     * cálculo es una mejora sobre el camino manual, no un reemplazo que deje al
     * operario sin salida cuando le falta un insumo.
     */
    public Precio resolveMezcla(Proveedor proveedor, int relacion,
                                BigDecimal precioAceite, BigDecimal precioManual) {
        // Un proveedor generico no tiene insumos propios de los que salga el
        // calculo: su nafta y su aceite vigentes son de otra estacion. Sin
        // insumos confiables se cae directo al camino manual (el paso 4), que
        // existe justamente para que esto nunca sea un callejon sin salida.
        if (proveedor.isGenerico()) {
            return altaSiempre(proveedor, TipoCombustible.MEZCLA, precioManual);
        }

        Precio aceite = resolveAceite(proveedor, precioAceite);
        Precio nafta = vigenteDe(proveedor, TipoCombustible.NAFTA_SUPER);
        Precio vigenteMezcla = vigenteDe(proveedor, TipoCombustible.MEZCLA);

        if (nafta != null && aceite != null) {
            BigDecimal calculado = MezclaCalculator.calcular(
                nafta.getPrecioUnitario(), aceite.getPrecioUnitario(), relacion);

            // compareTo ignora la escala (2254.9 == 2254.90). Si el cálculo no
            // movió el precio, no se abre una fila nueva por cada carga.
            if (vigenteMezcla != null
                && vigenteMezcla.getPrecioUnitario().compareTo(calculado) == 0) {
                return vigenteMezcla;
            }
            return reemplazarVigente(proveedor, TipoCombustible.MEZCLA, calculado);
        }

        if (vigenteMezcla != null) {
            return vigenteMezcla;
        }

        if (precioManual != null) {
            validarAltaInicial(precioManual, TipoCombustible.MEZCLA, "precioUnitario");
            return reemplazarVigente(proveedor, TipoCombustible.MEZCLA, precioManual);
        }

        throw new ResourceNotFoundException(
            ErrorCode.PRECIO_BASE_MEZCLA_NOT_FOUND,
            "No se puede resolver el precio de la mezcla: falta el precio de la nafta o el del"
                + " aceite de este proveedor. Ingresá el del aceite o el de la mezcla.",
            "precioAceite");
    }

    /**
     * El vigente de (proveedor, ACEITE), actualizándolo si el empleado informó
     * uno nuevo. Que el aceite viva en el catálogo es lo que hace que solo se
     * pida una vez por estación y no en cada carga.
     *
     * Se valida con la BANDA y nunca con el margen. El margen (±30) está
     * calibrado para combustibles de ~$2.000/L; el aceite ronda los $15.000/L y
     * se mueve en saltos mucho más grandes, así que ese tope trabaría casi
     * cualquier actualización legítima. La banda, en cambio, se arma con los
     * aceites vigentes de las demás estaciones, así que escala sola con el
     * producto (ver fueraDeBandaDeAlta y la exención de ACEITE en fueraDeMargen).
     */
    private Precio resolveAceite(Proveedor proveedor, BigDecimal precioAceite) {
        Precio vigente = vigenteDe(proveedor, TipoCombustible.ACEITE);
        if (precioAceite == null) {
            return vigente;
        }
        if (vigente != null && vigente.getPrecioUnitario().compareTo(precioAceite) == 0) {
            return vigente;
        }
        validarAltaInicial(precioAceite, TipoCombustible.ACEITE, "precioAceite");
        return reemplazarVigente(proveedor, TipoCombustible.ACEITE, precioAceite);
    }

    /**
     * Da de alta SIEMPRE un precio nuevo con el valor tipeado, sin mirar el
     * vigente. Es el camino del proveedor GENERICO ("Otros").
     *
     * Por qué no puede heredar: el catálogo asume que un precio vigente de
     * (proveedor, combustible) describe al mismo surtidor la próxima vez. Para
     * el genérico eso es falso por construcción — dos cargas seguidas son en
     * estaciones distintas —, así que su vigente es el precio de OTRO lado. Y
     * heredarlo no sería solo impreciso: resolvePorCombustible devuelve el
     * vigente sin mirar precioManual, o sea que el valor que el empleado leyó
     * del surtidor se descartaría en silencio y el gasto del ticket quedaría
     * calculado con el número equivocado.
     *
     * El control de cordura pasa a ser la BANDA (lo que cobran las demás
     * estaciones por ese combustible) y no el margen contra el vigente propio,
     * que acá compararía contra un surtidor sin relación con este.
     *
     * El vigente igual se reemplaza en vez de dejarse abierto: mantiene el
     * invariante de un solo vigente por (proveedor, combustible) y deja la
     * historia intacta, que es lo que los tickets viejos referencian.
     */
    private Precio altaSiempre(Proveedor proveedor, TipoCombustible tipoCombustible,
                               BigDecimal precioManual) {
        if (precioManual == null) {
            throw new ResourceNotFoundException(
                ErrorCode.PRECIO_NOT_FOUND_PARA_COMBUSTIBLE,
                "Una carga en un proveedor sin registrar no tiene precio de catálogo:"
                    + " ingresá el que pagaste.", "precioUnitario");
        }
        validarAltaInicial(precioManual, tipoCombustible, "precioUnitario");
        return reemplazarVigente(proveedor, tipoCombustible, precioManual);
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
        // ACEITE queda exento por una razón distinta a la de MEZCLA: el margen es
        // un valor ABSOLUTO calibrado para combustibles de ~$2.000/L. El aceite
        // ronda los $15.000/L y se mueve en saltos mucho mayores, así que ±30
        // trabaría casi cualquier actualización legítima. Su control es la banda
        // (ver resolveAceite), que se arma con los aceites de las demás
        // estaciones y escala sola con el producto.
        if (tipoCombustible == TipoCombustible.MEZCLA
            || tipoCombustible == TipoCombustible.ACEITE) {
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
