package com.retrorental.backend.service;

import com.retrorental.backend.dto.response.TicketAnalysisResponse;
import com.retrorental.backend.dto.response.TicketAnalysisResult;
import com.retrorental.backend.model.Precio;
import com.retrorental.backend.model.Proveedor;
import com.retrorental.backend.model.enums.Servicio;
import com.retrorental.backend.model.enums.TipoCombustible;
import com.retrorental.backend.repository.ProveedorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resuelve la salida del OCR contra el catálogo: a qué proveedor y a qué precio
 * corresponde el ticket que leyó Mistral.
 *
 * Extraído de TicketService (ver docs/BACKEND-AUDIT.md, SVC-01), pero la razón
 * de fondo es TX-01: esto es TODO lo que analyze() necesita hacer contra la base
 * de datos. Aislarlo en su propio bean permite que la llamada HTTP al OCR quede
 * FUERA de la transacción y que la transacción dure lo que duran tres consultas.
 *
 * Que sea otro bean no es un detalle de estilo: si este método viviera en
 * TicketService, llamarlo desde analyze() sería una self-invocation y el proxy
 * de Spring NO abriría la transacción. El arreglo de TX-01 se caería en silencio.
 *
 * OJO: además de leer, esto ESCRIBE. Si el OCR devuelve un proveedor o un precio
 * que no existen en el catálogo, los da de alta. Por eso es @Transactional de
 * escritura y no readOnly.
 */
@Service
@RequiredArgsConstructor
public class CatalogoOcrResolver {

    private final ProveedorRepository proveedorRepository;
    private final PrecioCatalogoService precioCatalogo;

    /**
     * Resuelve proveedor y precio para lo que leyó el OCR y arma la respuesta
     * del endpoint de análisis.
     *
     * Transacción CORTA a propósito: acá adentro no hay una sola llamada de red.
     * El OCR ya corrió antes, sin conexión de base tomada (ver
     * TicketService.analyze).
     */
    @Transactional
    public TicketAnalysisResponse resolver(TicketAnalysisResult ocr) {
        Proveedor proveedor = resolveProveedor(ocr.estacion(), ocr.cuit());
        // El precio se resuelve PARA el proveedor: cada estación tiene su propio
        // precio del combustible. Si no se pudo resolver el proveedor, no hay a
        // quién asociar el precio → queda en blanco.
        Precio precio = resolvePrecio(proveedor, ocr.tipoCombustible(), ocr.precioPorLitro());

        return new TicketAnalysisResponse(
            ocr.litros(),
            ocr.fechaCarga(),
            ocr.importeTotal(),
            ocr.precioPorLitro(),
            ocr.estacion(),
            ocr.tipoCombustible(),
            proveedor != null ? proveedor.getId() : null,
            proveedor != null ? proveedor.getNombre() : null,
            precio != null ? precio.getId() : null,
            precio != null ? precio.getPrecioUnitario() : null
        );
    }

    // Resuelve el proveedor para la estación leída. Si no hay match existente y
    // el OCR trae los datos necesarios (nombre + CUIT), lo crea automáticamente.
    // Sin CUIT no se puede crear (columna NOT NULL): se deja null para carga
    // manual.
    private Proveedor resolveProveedor(String estacion, String cuit) {
        if (estacion == null || estacion.isBlank()) {
            return null;
        }

        Proveedor matched = matchProveedor(estacion);
        if (matched != null) {
            return matched;
        }

        if (cuit == null || cuit.isBlank()) {
            return null;
        }
        String cuitLimpio = cuit.trim();

        // Idempotencia: si ya existe uno con ese CUIT, se reutiliza en vez de
        // crear un duplicado (analyze puede llamarse varias veces).
        return proveedorRepository.findByCuit(cuitLimpio).orElseGet(() -> {
            Proveedor nuevo = new Proveedor();
            nuevo.setNombre(estacion.trim());
            nuevo.setCuit(cuitLimpio);
            nuevo.setServicio(Servicio.COMBUSTIBLE);
            return proveedorRepository.save(nuevo);
        });
    }

    // Resuelve el precio del ticket PARA UN PROVEEDOR, dándole prioridad sobre el
    // catálogo (que puede estar desactualizado). El precio es por (proveedor,
    // combustible): distintas estaciones tienen precios distintos del mismo
    // producto.
    //
    // - Sin proveedor, sin combustible o sin precio leído → null: "dejar vacío lo
    //   que no se leyó"; el empleado completa eligiendo el vehículo.
    // - Si el vigente de ese (proveedor, combustible) YA coincide → se reutiliza.
    // - Si difiere (o no hay) → el precio leído pasa a ser el NUEVO vigente de ese
    //   (proveedor, combustible), cerrando el anterior.
    //
    // Cuando el proveedor es NUEVO (recién dado de alta desde el ticket), esto
    // carga SOLO el precio del combustible leído; los demás combustibles quedan
    // sin precio hasta que aparezcan en otro ticket.
    private Precio resolvePrecio(Proveedor proveedor, TipoCombustible tipoCombustible,
                                 Double precioPorLitro) {
        if (proveedor == null || tipoCombustible == null || precioPorLitro == null) {
            return null;
        }

        BigDecimal objetivo = BigDecimal.valueOf(precioPorLitro);
        Precio vigente = precioCatalogo.vigenteDe(proveedor, tipoCombustible);

        // compareTo ignora la escala (2083 == 2083.00).
        if (vigente != null && vigente.getPrecioUnitario() != null
            && vigente.getPrecioUnitario().compareTo(objetivo) == 0) {
            return vigente;
        }

        // El OCR puede alucinar: leer 2.086 como 20.860 es un error de una coma.
        // Antes, ese valor entraba al catalogo sin control y quedaba como precio
        // vigente para TODOS los empleados. Si se aleja demasiado del vigente se
        // descarta y se devuelve el vigente: aca NO se lanza excepcion, porque
        // esto alimenta una sugerencia de formulario y trabar el analisis por un
        // precio dudoso dejaria al empleado sin poder cargar.
        if (vigente != null
            && precioCatalogo.fueraDeMargen(objetivo, vigente.getPrecioUnitario(), tipoCombustible)) {
            return vigente;
        }

        // Mismo control cuando NO hay vigente contra el cual medir: el proveedor
        // recien dado de alta desde este mismo ticket nace sin precios, y hasta
        // aca lo que leyera el OCR entraba al catalogo sin ningun filtro. Es el
        // peor de los dos casos, no el mas leve: el precio que se crea queda
        // vigente para todos los que carguen despues en esa estacion. Se mide
        // contra lo que cobran las demas estaciones por ese combustible (ver
        // fueraDeBandaDeAlta).
        //
        // Tampoco lanza, por el mismo motivo que arriba: devuelve vigente, que en
        // el alta es null. El formulario muestra el precio en blanco y el empleado
        // lo tipea, en vez de arrastrar la alucinacion del OCR ya prellenada.
        if (precioCatalogo.fueraDeBandaDeAlta(objetivo, tipoCombustible)) {
            return vigente;
        }

        return precioCatalogo.reemplazarVigente(proveedor, tipoCombustible, objetivo);
    }

    // Busca un proveedor de COMBUSTIBLE cuyo nombre coincida (contención, sin
    // distinguir mayúsculas) con el texto "estacion" que leyó el OCR. Solo se
    // considera match si es ÚNICO: con 0 o varios candidatos se devuelve null.
    private Proveedor matchProveedor(String estacion) {
        if (estacion == null || estacion.isBlank()) {
            return null;
        }
        String needle = estacion.toLowerCase();
        List<Proveedor> candidatos = proveedorRepository.findByServicio(Servicio.COMBUSTIBLE).stream()
            // El generico ("Otros") nunca se matchea desde el OCR: no es una
            // estacion que el ticket pueda nombrar, es la salida MANUAL para
            // cuando el operario decide no dar de alta la que cargó. Sin este
            // filtro, un nombre leído que contenga "otros" lo elegiría solo.
            .filter(p -> !p.isGenerico())
            .filter(p -> {
                String nombre = p.getNombre() == null ? "" : p.getNombre().toLowerCase();
                return !nombre.isBlank() && (needle.contains(nombre) || nombre.contains(needle));
            })
            .toList();
        return candidatos.size() == 1 ? candidatos.get(0) : null;
    }
}
