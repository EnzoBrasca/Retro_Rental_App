package com.retrorental.backend.service;

import com.retrorental.backend.dto.request.CreateTicketRequest;
import com.retrorental.backend.dto.response.TicketAnalysisResponse;
import com.retrorental.backend.dto.response.TicketResponse;
import com.retrorental.backend.exception.ConflictException;
import com.retrorental.backend.exception.ErrorCode;
import com.retrorental.backend.exception.ResourceNotFoundException;
import com.retrorental.backend.exception.TicketAnalysisException;
import com.retrorental.backend.model.Empleado;
import com.retrorental.backend.model.Herramienta;
import com.retrorental.backend.model.Persona;
import com.retrorental.backend.model.Precio;
import com.retrorental.backend.model.Proveedor;
import com.retrorental.backend.model.Ticket;
import com.retrorental.backend.model.Vehiculo;
import com.retrorental.backend.model.enums.Estado;
import com.retrorental.backend.model.enums.Servicio;
import com.retrorental.backend.model.enums.TipoCombustible;
import com.retrorental.backend.repository.HerramientaRepository;
import com.retrorental.backend.repository.PersonaRepository;
import com.retrorental.backend.repository.PrecioRepository;
import com.retrorental.backend.repository.ProveedorRepository;
import com.retrorental.backend.repository.TicketRepository;
import com.retrorental.backend.repository.VehiculoRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final PrecioRepository precioRepository;
    private final ProveedorRepository proveedorRepository;
    private final VehiculoRepository vehiculoRepository;
    private final HerramientaRepository herramientaRepository;
    private final PersonaRepository personaRepository;
    private final StorageService storageService;
    // Opcional: el bean de análisis solo existe si hay API key de Mistral. Crear
    // tickets NO debe depender de eso, por eso se inyecta con ObjectProvider.
    private final ObjectProvider<TicketAnalysisService> analysisProvider;

    // Cuanto puede alejarse del vigente un precio corregido a mano (o leido por
    // OCR) antes de considerarse un error de carga. Se inyecta por campo y no
    // por constructor para no reescribir el @RequiredArgsConstructor entero por
    // un solo escalar de configuracion.
    @Value("${app.precio.margen-maximo:30}")
    private BigDecimal margenMaximo;

    // Cuantos intervalos mira el consumo "reciente" del vehiculo.
    @Value("${app.consumo.ventana-cargas:10}")
    private int ventanaConsumo;

    @Transactional
    public TicketResponse create(CreateTicketRequest request, String empleadoUsername) {
        Persona persona = resolvePersona(empleadoUsername);

        // Se resuelve el proveedor ANTES que el precio (y ANTES de subir archivos,
        // para no dejar objetos huerfanos en MinIO si el request es invalido):
        // la resolucion del precio de una herramienta depende de el (ver
        // resolvePrecioPorCombustible).
        Proveedor proveedor = proveedorRepository.findById(request.getIdProveedor())
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.PROVEEDOR_NOT_FOUND, "No existe el proveedor indicado", "idProveedor"));

        // Vehiculo: idPrecio ya trae el catalogo resuelto. Herramienta: manda
        // tipoCombustible en su lugar (no hay un idPrecio previo que elegir), y
        // el precio se resuelve/crea aca (ver resolvePrecioPorCombustible). Cual
        // de los dos vino lo garantiza @OrigenCargaCoherente.
        Precio precio = request.getIdPrecio() != null
            ? resolvePrecioPorId(request.getIdPrecio(), proveedor)
            : resolvePrecioPorCombustible(proveedor, request.getTipoCombustible());

        // Precio realmente pagado. Si el empleado lo corrigio (el del catalogo
        // estaba desactualizado), se valida el margen y el catalogo se actualiza:
        // el proximo que cargue ve el precio corregido. Sin esto el catalogo
        // envejeceria y el propio margen terminaria bloqueando cargas legitimas.
        // La MEZCLA no tiene tope: su precio se aleja legitimamente del de la
        // nafta base que tomo como referencia (ver validarMargen).
        Precio precioAplicado = precio;
        if (request.getPrecioUnitario() != null
            && precio.getPrecioUnitario().compareTo(request.getPrecioUnitario()) != 0) {
            validarMargen(request.getPrecioUnitario(), precio.getPrecioUnitario(),
                precio.getTipoCombustible(), "precioUnitario");
            precioAplicado = reemplazarVigente(
                proveedor, precio.getTipoCombustible(), request.getPrecioUnitario());
        }

        // Origen de la carga: EXACTAMENTE uno de los dos (lo garantiza
        // @OrigenCargaCoherente en el request y el CHECK de la base). Una
        // herramienta no tiene contador: no hay lectura que validar ni
        // operario que actualizar.
        Vehiculo vehiculo = null;
        Herramienta herramienta;
        if (request.getIdVehiculo() != null) {
            vehiculo = resolveVehiculoCargable(request.getIdVehiculo());

            // Un odometro/horometro no retrocede. Si la lectura es menor que la
            // ultima registrada, casi siempre es un error de tipeo: se rechaza con el
            // valor anterior a la vista para que el empleado pueda corregirlo.
            Integer usoPrevio = vehiculo.getUsoAcumulado();
            if (usoPrevio != null && request.getUsoAcumulado() < usoPrevio) {
                throw new ConflictException(
                    ErrorCode.USO_ACUMULADO_RETROCEDE,
                    "La lectura (" + request.getUsoAcumulado() + ") es menor que la ultima registrada ("
                        + usoPrevio + ") para este vehiculo",
                    "usoAcumulado");
            }
            // La ficha del vehiculo queda con la ultima lectura conocida.
            vehiculo.setUsoAcumulado(request.getUsoAcumulado());

            // Pool compartido: el operario "actual" del vehiculo se actualiza en cada
            // carga al empleado que la registra. Así la card de la flota muestra quién
            // lo usó por última vez, pisándose cada vez que otro operario le carga.
            // Vehiculo.operario es de tipo Empleado, así que solo se actualiza cuando
            // quien carga es un empleado; un administrador puede cargar tickets pero
            // no "toma" el vehiculo como operario del pool.
            if (persona instanceof Empleado emp) {
                vehiculo.setOperario(emp);
            }
            herramienta = null;
        } else {
            herramienta = resolveHerramientaCargable(request.getIdHerramienta());
        }

        // Subimos a MinIO y guardamos SOLO la key (la URL presignada expira).
        // La foto del ticket es OPCIONAL (ver CreateTicketRequest): muchos empleados
        // en campo, con teléfonos de gama baja o baja alfabetización digital, cargan
        // sin comprobante. Solo se sube si el cliente la envió; sin ella, key null.
        String ticketFotoKey = request.getTicketFoto() != null && !request.getTicketFoto().isEmpty()
            ? storageService.upload(request.getTicketFoto(), "tickets")
            : null;
        // La foto del tablero también es opcional: solo se sube si vino.
        String tableroFotoKey = request.getTableroFoto() != null && !request.getTableroFoto().isEmpty()
            ? storageService.upload(request.getTableroFoto(), "tableros")
            : null;

        Ticket ticket = new Ticket();
        ticket.setLitros(request.getLitros());
        ticket.setFechaCarga(request.getFechaCarga() != null ? request.getFechaCarga() : LocalDateTime.now());
        ticket.setPrecio(precioAplicado);
        // usoAcumulado solo tiene sentido para un vehiculo: una herramienta no
        // tiene contador, por eso no viene en el request cuando el origen es
        // idHerramienta (@OrigenCargaCoherente lo garantiza).
        ticket.setUsoAcumulado(vehiculo != null ? request.getUsoAcumulado() : null);
        ticket.setProveedor(proveedor);
        ticket.setPersona(persona);
        ticket.setVehiculo(vehiculo);
        ticket.setHerramienta(herramienta);
        ticket.setTicketFotoUrl(ticketFotoKey);
        ticket.setTableroFotoUrl(tableroFotoKey);

        Ticket guardado = ticketRepository.save(ticket);

        // El consumo/lectura del vehiculo solo se recalcula cuando el ticket
        // ES de un vehiculo. Una herramienta no tiene contador ni consumo
        // promedio: sus cargas NUNCA participan de este calculo.
        if (vehiculo != null) {
            // Recien ahora, con el ticket ya persistido, el consumo se recalcula
            // incluyendolo. El vehiculo se guarda una sola vez con todo junto: la
            // lectura nueva, el operario y el consumo.
            recalcularConsumo(vehiculo);
            vehiculoRepository.save(vehiculo);
        }

        return toResponse(guardado);
    }

    /**
     * Recalcula el consumo del vehiculo a partir de sus cargas.
     *
     * Se hace en cada alta de ticket y no al leer para que el listado de la
     * flota no dispare una consulta por vehiculo. El costo es una consulta por
     * carga, que es la operacion poco frecuente de las dos.
     *
     * Si no hay datos suficientes (hacen falta dos cargas con lectura), se
     * vuelve a consumoInicial: la estimacion que cargo el admin en el alta.
     *
     * Ese fallback existe por la ANULACION. Mientras los tickets solo se
     * agregaban, alcanzaba con no tocar consumoPromedio cuando el calculo no
     * daba: el valor que habia era justamente la estimacion del alta. Al poder
     * anular, el vehiculo puede RETROCEDER a menos de dos cargas, y entonces
     * "no tocar" dejaria un consumo calculado a partir de cargas que ya no
     * existen. Un numero que sobrevive a la evidencia que lo sustentaba.
     *
     * Si consumoInicial es null (vehiculos anteriores a V7, cuya estimacion
     * original ya se habia perdido) no hay a que volver: se deja lo que hay,
     * que es lo unico que se puede hacer sin inventar un dato.
     */
    private void recalcularConsumo(Vehiculo vehiculo) {
        List<ConsumoCalculator.Carga> cargas = ticketRepository
            .findByVehiculoIdAndUsoAcumuladoIsNotNullAndFechaAnulacionIsNullOrderByUsoAcumuladoAsc(vehiculo.getId())
            .stream()
            .map(t -> new ConsumoCalculator.Carga(t.getUsoAcumulado(), t.getLitros()))
            .toList();

        ConsumoCalculator.Consumo consumo = ConsumoCalculator.calcular(
            cargas, vehiculo.getTipoVehiculo().unidadUso(), ventanaConsumo);

        if (consumo.historico() != null) {
            vehiculo.setConsumoPromedio(consumo.historico());
        } else if (vehiculo.getConsumoInicial() != null) {
            vehiculo.setConsumoPromedio(vehiculo.getConsumoInicial());
        }
        vehiculo.setConsumoReciente(consumo.reciente());
    }

    /**
     * Recalcula la lectura del contador del vehiculo desde sus cargas vigentes.
     *
     * Es lo que hace util a la anulacion. El motivo mas comun para anular un
     * ticket es un error de tipeo en la lectura (99999 en vez de 9999), y esa
     * lectura equivocada quedo copiada en vehiculo.usoAcumulado. Sin revertirla,
     * toda carga futura del vehiculo se rechazaria con USO_ACUMULADO_RETROCEDE
     * contra un numero que ya nadie puede justificar: la funcion que el admin
     * usa para arreglar el error lo dejaria sin poder arreglarlo.
     *
     * Sin cargas vigentes no se toca: la lectura previa a todos los tickets no
     * se guarda en ningun lado. El admin la corrige a mano desde el ABM.
     */
    private void recalcularUsoAcumulado(Vehiculo vehiculo) {
        ticketRepository
            .findByVehiculoIdAndUsoAcumuladoIsNotNullAndFechaAnulacionIsNullOrderByUsoAcumuladoAsc(vehiculo.getId())
            .stream()
            .map(Ticket::getUsoAcumulado)
            .max(Integer::compareTo)
            .ifPresent(vehiculo::setUsoAcumulado);
    }

    /**
     * Anula un ticket (solo admin). Baja logica: la fila queda porque un ticket
     * es un registro contable, y con ella queda quien lo anulo y cuando.
     *
     * Anular NO es solo marcar la fila. El alta del ticket habia adelantado la
     * lectura del vehiculo y recalculado su consumo; las dos cosas se deshacen
     * aca a partir de las cargas que siguen vigentes.
     */
    @Transactional
    public void anular(Integer id, String adminUsername) {
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.TICKET_NOT_FOUND, "Ticket no encontrado"));

        if (ticket.estaAnulado()) {
            throw new ConflictException(
                ErrorCode.TICKET_ALREADY_ANULADO, "El ticket ya está anulado");
        }

        Persona admin = personaRepository.findByUsername(adminUsername)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.USER_NOT_FOUND, "Usuario no encontrado"));

        ticket.setFechaAnulacion(LocalDateTime.now());
        ticket.setAnuladoPor(admin);
        ticketRepository.save(ticket);

        // Recien con el ticket ya marcado, las dos consultas de abajo lo ven
        // como anulado y lo excluyen. Si se hiciera antes del save, el ticket
        // seguiria contando y no se revertiria nada.
        //
        // Un ticket de herramienta no tiene vehiculo que revertir: no adelanto
        // ninguna lectura ni consumo, asi que no hay nada que recalcular.
        Vehiculo vehiculo = ticket.getVehiculo();
        if (vehiculo != null) {
            recalcularUsoAcumulado(vehiculo);
            recalcularConsumo(vehiculo);
            vehiculoRepository.save(vehiculo);
        }

        // Las fotos NO se borran del storage: son el respaldo del comprobante y
        // la anulacion es reversible.
    }

    /**
     * Analiza la foto de un ticket con el OCR y lo resuelve contra el catálogo
     * para pre-cargar el formulario de creación.
     *
     * ATENCIÓN: además de leer, este método PUEDE ESCRIBIR. Si el OCR devuelve
     * un proveedor o un precio que no existe en el catálogo, los da de alta
     * automáticamente (ver resolveProveedor/resolvePrecio). Por eso es
     * transaccional de escritura, no readOnly.
     */
    @Transactional
    public TicketAnalysisResponse analyze(MultipartFile ticketFoto) {
        TicketAnalysisService analysisService = analysisProvider.getIfAvailable();
        if (analysisService == null) {
            throw new TicketAnalysisException(ErrorCode.ANALYSIS_UNAVAILABLE,
                "El análisis de tickets no está configurado (falta la API key de Mistral)");
        }

        var ocr = analysisService.analyze(ticketFoto);

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
    //   (proveedor, combustible), cerrando el anterior (invariante: un único
    //   vigente por proveedor+producto). Nunca se muta un Precio existente porque
    //   hay tickets históricos que lo referencian; siempre se crea una fila nueva.
    //
    // Cuando el proveedor es NUEVO (recién dado de alta desde el ticket), esto
    // carga SOLO el precio del combustible leído; los demás combustibles quedan
    // sin precio hasta que aparezcan en otro ticket.
    private Precio resolvePrecio(Proveedor proveedor, TipoCombustible tipoCombustible, Double precioPorLitro) {
        if (proveedor == null || tipoCombustible == null || precioPorLitro == null) {
            return null;
        }

        BigDecimal objetivo = BigDecimal.valueOf(precioPorLitro);
        Precio vigente = precioRepository
            .findByProveedorAndTipoCombustibleAndFechaHastaIsNull(proveedor, tipoCombustible)
            .orElse(null);

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
        if (vigente != null && fueraDeMargen(objetivo, vigente.getPrecioUnitario(), tipoCombustible)) {
            return vigente;
        }

        return reemplazarVigente(proveedor, tipoCombustible, objetivo);
    }

    // Resuelve el precio de una carga de VEHICULO (idPrecio ya elegido en el
    // formulario, resuelto contra el catalogo). Valida coherencia
    // precio↔proveedor: si el precio pertenece a una estación, debe ser la
    // MISMA que la del ticket. El precio es por (proveedor, combustible), así
    // que cargar una compra con el precio de OTRA estación sería un dato
    // inconsistente. (Precios legacy sin proveedor no se validan.)
    private Precio resolvePrecioPorId(Integer idPrecio, Proveedor proveedor) {
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
     * Resuelve el precio de una carga de HERRAMIENTA, que no manda idPrecio
     * (no hay uno resuelto de antemano: el combustible se elige carga por
     * carga) sino el tipoCombustible elegido.
     *
     * - Si el proveedor ya tiene un vigente de ese combustible, se usa tal cual.
     * - Si NO lo tiene y es MEZCLA (nafta con aceite): no es un producto que
     *   vendan las estaciones (no tiene precio propio en el catálogo), así que
     *   se crea copiando como valor inicial el vigente de NAFTA_SUPER del MISMO
     *   proveedor. El empleado ve ese valor y lo corrige al precio real de la
     *   mezcla (sin tope de margen, ver fueraDeMargen). Si el proveedor tampoco
     *   tiene ese NAFTA_SUPER, no hay de donde copiar: error explícito, no se
     *   inventa un precio en cero.
     * - Para cualquier otro combustible sin vigente (ej. un bidón de GASOIL en
     *   un proveedor que no lo tiene cargado): error explícito. A diferencia de
     *   MEZCLA, ningún otro combustible tiene una referencia de la que copiar.
     */
    private Precio resolvePrecioPorCombustible(Proveedor proveedor, TipoCombustible tipoCombustible) {
        Precio vigente = precioRepository
            .findByProveedorAndTipoCombustibleAndFechaHastaIsNull(proveedor, tipoCombustible)
            .orElse(null);
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

    /**
     * Retira el precio vigente de (proveedor, combustible) y deja uno nuevo.
     *
     * Conserva la historia: el anterior queda con fechaHasta = hoy en vez de
     * borrarse, asi los tickets viejos siguen apuntando al precio que realmente
     * se pago. Lo comparten la correccion manual del empleado y la ruta del OCR.
     */
    private Precio reemplazarVigente(Proveedor proveedor, TipoCombustible tipoCombustible,
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
     * Si el precio propuesto se aleja del vigente mas de lo tolerado.
     *
     * La MEZCLA (nafta con aceite, motosierra) queda SIN TOPE: cuando el
     * proveedor no tiene un vigente propio, se crea tomando como base el
     * vigente de NAFTA_SUPER de ese proveedor (ver
     * resolvePrecioPorCombustible), y el usuario lo corrige despues al precio
     * real de la mezcla, que lleva aceite y por eso se aleja legitimamente del
     * de la nafta pura. Aplicarle el mismo margen que al resto trabaria
     * correcciones validas.
     */
    private boolean fueraDeMargen(BigDecimal propuesto, BigDecimal vigente, TipoCombustible tipoCombustible) {
        if (propuesto == null || vigente == null) {
            return false;
        }
        if (tipoCombustible == TipoCombustible.MEZCLA) {
            return false;
        }
        return propuesto.subtract(vigente).abs().compareTo(margenMaximo) > 0;
    }

    /** Igual que fueraDeMargen pero cortando la operacion: la usa la carga manual. */
    private void validarMargen(BigDecimal propuesto, BigDecimal vigente,
                               TipoCombustible tipoCombustible, String campo) {
        if (fueraDeMargen(propuesto, vigente, tipoCombustible)) {
            throw new ConflictException(
                ErrorCode.PRECIO_FUERA_DE_RANGO,
                "El precio ingresado (" + propuesto + ") se aleja mas de " + margenMaximo
                    + " del precio vigente (" + vigente + "). Verificá el importe.",
                campo);
        }
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
            .filter(p -> {
                String nombre = p.getNombre() == null ? "" : p.getNombre().toLowerCase();
                return !nombre.isBlank() && (needle.contains(nombre) || nombre.contains(needle));
            })
            .toList();
        return candidatos.size() == 1 ? candidatos.get(0) : null;
    }

    /**
     * Listado paginado de tickets para el administrador. Todos los filtros son
     * opcionales; sin ninguno devuelve todos. El mapeo a TicketResponse (que
     * genera URLs presignadas) ocurre dentro de la transacción para que las
     * asociaciones LAZY del Ticket se resuelvan con la sesión abierta.
     */
    @Transactional(readOnly = true)
    public PagedModel<TicketResponse> listForAdmin(
            Integer empleadoId, Integer proveedorId, Integer vehiculoId,
            LocalDateTime desde, LocalDateTime hasta,
            BigDecimal montoMin, BigDecimal montoMax, boolean incluirAnulados,
            Pageable pageable) {
        // Se construye la Specification agregando SOLO los filtros presentes.
        // Con Criteria API se omite el predicado cuando el filtro es null, en
        // vez de un ":param IS NULL OR ..." (que en Postgres rompe con
        // "could not determine data type" al pasar un timestamp null).
        Specification<Ticket> spec = (root, query, cb) -> {
            // Evita el N+1: trae empleado, proveedor y precio en la MISMA query
            // (JOIN FETCH). Solo en la query de datos, no en la de count — un
            // fetch en el count rompe. Son todos @ManyToOne (single-valued), así
            // que el fetch join es seguro con paginación.
            if (query != null && query.getResultType() != Long.class
                    && query.getResultType() != long.class) {
                root.fetch("persona");
                root.fetch("proveedor");
                root.fetch("precio");
                root.fetch("vehiculo");
            }

            List<Predicate> predicates = new ArrayList<>();
            if (empleadoId != null) {
                predicates.add(cb.equal(root.get("persona").get("id"), empleadoId));
            }
            if (proveedorId != null) {
                predicates.add(cb.equal(root.get("proveedor").get("id"), proveedorId));
            }
            if (vehiculoId != null) {
                predicates.add(cb.equal(root.get("vehiculo").get("id"), vehiculoId));
            }
            if (desde != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("fechaCarga"), desde));
            }
            if (hasta != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("fechaCarga"), hasta));
            }
            // Por defecto el listado muestra solo los VIGENTES. Los anulados se
            // piden explicitamente: son la excepcion, no el caso normal.
            if (!incluirAnulados) {
                predicates.add(cb.isNull(root.get("fechaAnulacion")));
            }
            // El monto NO es una columna: es litros x precio unitario. Se
            // compara como expresion para no tener que desnormalizar un total
            // que quedaria desincronizado apenas se corrija un precio.
            if (montoMin != null || montoMax != null) {
                Expression<BigDecimal> monto = cb.prod(
                    root.get("precio").<BigDecimal>get("precioUnitario"),
                    root.get("litros").as(BigDecimal.class));
                if (montoMin != null) {
                    predicates.add(cb.greaterThanOrEqualTo(monto, montoMin));
                }
                if (montoMax != null) {
                    predicates.add(cb.lessThanOrEqualTo(monto, montoMax));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return new PagedModel<>(
            ticketRepository.findAll(spec, pageable).map(this::toResponse)
        );
    }

    @Transactional(readOnly = true)
    public TicketResponse get(Integer id) {
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.TICKET_NOT_FOUND, "Ticket no encontrado"));
        return toResponse(ticket);
    }

    /**
     * Tickets del empleado autenticado, más recientes primero. Alimenta la
     * pestaña "Historial" de la app del empleado. El empleado sale del JWT, no
     * de un parámetro, para que solo pueda ver los suyos.
     */
    @Transactional(readOnly = true)
    public List<TicketResponse> listMine(String empleadoUsername) {
        Persona persona = resolvePersona(empleadoUsername);
        return ticketRepository.findByPersonaIdAndFechaAnulacionIsNullOrderByFechaCargaDesc(persona.getId())
            .stream().map(this::toResponse).toList();
    }

    private Persona resolvePersona(String username) {
        return personaRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.USER_NOT_FOUND, "Usuario no encontrado"));
    }

    // Valida que el vehiculo se pueda cargar: que exista (404), que no esté dado
    // de baja y que no esté en mantenimiento (409). Modelo de pool compartido:
    // cualquier empleado activo puede cargar cualquier vehiculo operativo, NO se
    // exige asignación previa. Quién cargó queda registrado en el propio ticket
    // (campo empleado), así que la trazabilidad no depende de una asignación.
    private Vehiculo resolveVehiculoCargable(Integer vehiculoId) {
        Vehiculo vehiculo = vehiculoRepository.findById(vehiculoId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.VEHICULO_NOT_FOUND, "No existe el vehiculo indicado", "idVehiculo"));

        // No se pueden cargar tickets sobre un vehiculo dado de baja, aunque el
        // cliente tenga una vista vieja donde todavía aparezca.
        if (vehiculo.getFechaBaja() != null) {
            throw new ConflictException(
                ErrorCode.VEHICULO_ALREADY_INACTIVE, "El vehiculo está dado de baja", "idVehiculo");
        }

        // Un vehiculo en mantenimiento está fuera de operación: no se le cargan
        // tickets aunque el cliente lo muestre.
        if (vehiculo.getEstado() == Estado.EN_MANTENIMIENTO) {
            throw new ConflictException(
                ErrorCode.VEHICULO_NOT_AVAILABLE, "El vehiculo está en mantenimiento", "idVehiculo");
        }
        return vehiculo;
    }

    // Valida que la herramienta se pueda cargar: que exista (404) y que no
    // esté dada de baja (409). No hay chequeo de "mantenimiento" ni pool de
    // operario: una herramienta no tiene Estado ni asignación, solo nombre y
    // capacidad.
    private Herramienta resolveHerramientaCargable(Integer herramientaId) {
        Herramienta herramienta = herramientaRepository.findById(herramientaId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.HERRAMIENTA_NOT_FOUND, "No existe la herramienta indicada", "idHerramienta"));

        if (herramienta.getFechaBaja() != null) {
            throw new ConflictException(
                ErrorCode.HERRAMIENTA_ALREADY_INACTIVE, "La herramienta está dada de baja", "idHerramienta");
        }
        return herramienta;
    }

    // Construye la respuesta generando URLs presignadas frescas a partir de las
    // keys almacenadas. La URL NUNCA se persiste: se calcula en cada lectura.
    private TicketResponse toResponse(Ticket ticket) {
        // Tanto la foto del ticket como la del tablero son opcionales: si no hay key,
        // no se genera URL (evita pedirle a MinIO una presignada para un objeto
        // inexistente, que además rompería con una key null).
        String ticketKey = ticket.getTicketFotoUrl();
        String tableroKey = ticket.getTableroFotoUrl();
        // Exactamente uno de vehiculo/herramienta viene con valor. unidadUso
        // se deriva del vehiculo; una carga de herramienta no tiene unidad
        // (no tiene contador).
        Vehiculo vehiculo = ticket.getVehiculo();
        Herramienta herramienta = ticket.getHerramienta();
        return new TicketResponse(
            ticket.getId(),
            ticket.getLitros(),
            ticket.getFechaCarga(),
            ticket.getPrecio().getId(),
            ticket.getProveedor().getId(),
            vehiculo != null ? vehiculo.getId() : null,
            herramienta != null ? herramienta.getId() : null,
            ticket.getPersona().getUsername(),
            ticket.getUsoAcumulado(),
            vehiculo != null ? vehiculo.getTipoVehiculo().unidadUso() : null,
            ticket.getPrecio().getPrecioUnitario(),
            ticketKey,
            ticketKey != null ? storageService.getUrl(ticketKey) : null,
            tableroKey,
            tableroKey != null ? storageService.getUrl(tableroKey) : null,
            ticket.getFechaAnulacion(),
            ticket.getAnuladoPor() != null ? ticket.getAnuladoPor().getUsername() : null
        );
    }
}
