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
import com.retrorental.backend.model.enums.Rol;
import com.retrorental.backend.model.enums.TipoCombustible;
import com.retrorental.backend.repository.HerramientaRepository;
import com.retrorental.backend.repository.PersonaRepository;
import com.retrorental.backend.repository.ProveedorRepository;
import com.retrorental.backend.repository.TicketRepository;
import com.retrorental.backend.repository.VehiculoRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final ProveedorRepository proveedorRepository;
    private final VehiculoRepository vehiculoRepository;
    private final HerramientaRepository herramientaRepository;
    private final PersonaRepository personaRepository;
    private final StorageService storageService;
    private final ImageValidator imageValidator;
    // Reglas del catalogo de precios (resolucion, reemplazo del vigente, margen)
    // y recalculo del consumo del vehiculo. Estaban adentro de esta clase, que
    // habia acumulado seis responsabilidades en 759 lineas (ver
    // docs/BACKEND-AUDIT.md, SVC-01).
    private final PrecioCatalogoService precioCatalogo;
    private final VehiculoConsumoService vehiculoConsumo;
    // Resolucion del OCR contra el catalogo. Bean SEPARADO a proposito: es lo
    // que permite que analyze() deje la llamada HTTP fuera de la transaccion
    // (ver TX-01 y el javadoc de analyze).
    private final CatalogoOcrResolver catalogoOcrResolver;
    // Opcional: el bean de análisis solo existe si hay API key de Mistral. Crear
    // tickets NO debe depender de eso, por eso se inyecta con ObjectProvider.
    private final ObjectProvider<TicketAnalysisService> analysisProvider;

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
        // el precio se resuelve/crea aca. Cual de los dos vino lo garantiza
        // @OrigenCargaCoherente.
        //
        // Tercer caso: un VEHICULO cuyo proveedor todavia no tiene precio de su
        // combustible (tipico del proveedor recien dado de alta desde un ticket,
        // que nace sin precios). Ahi no hay idPrecio que elegir en el formulario,
        // asi que el empleado tipea el precio y el request viaja sin idPrecio. El
        // combustible NO viaja: se lee del vehiculo, que lo tiene fijo, y asi el
        // contrato "un vehiculo no manda tipoCombustible" queda intacto.
        //
        // Los DOS origenes se resuelven ACA y no mas abajo justamente por eso: el
        // precio depende del combustible del vehiculo, y el de la mezcla depende
        // ademas de la relacion nafta:aceite de la herramienta.
        Vehiculo vehiculo = request.getIdVehiculo() != null
            ? resolveVehiculoCargable(request.getIdVehiculo())
            : null;
        Herramienta herramienta = request.getIdHerramienta() != null
            ? resolveHerramientaCargable(request.getIdHerramienta())
            : null;

        Precio precio;
        if (request.getIdPrecio() != null) {
            precio = precioCatalogo.resolvePorId(request.getIdPrecio(), proveedor);
        } else {
            TipoCombustible combustible = vehiculo != null
                ? vehiculo.getTipoCombustible()
                : request.getTipoCombustible();

            // La MEZCLA tiene camino propio porque su precio se CALCULA a partir
            // del de la nafta, el del aceite y la relacion de la maquina, en vez
            // de elegirse del catalogo (ver PrecioCatalogoService.resolveMezcla).
            // Solo puede venir de una herramienta: ningun vehiculo carga mezcla,
            // lo garantiza el CHECK de vehiculos.tipo_combustible.
            precio = combustible == TipoCombustible.MEZCLA && herramienta != null
                ? precioCatalogo.resolveMezcla(proveedor, herramienta.getRelacionMezcla(),
                    request.getPrecioAceite(), request.getPrecioUnitario())
                : precioCatalogo.resolvePorCombustible(
                    proveedor, combustible, request.getPrecioUnitario());
        }

        // Precio realmente pagado. Si el empleado lo corrigio (el del catalogo
        // estaba desactualizado), se valida el margen y el catalogo se actualiza:
        // el proximo que cargue ve el precio corregido. Sin esto el catalogo
        // envejeceria y el propio margen terminaria bloqueando cargas legitimas.
        // La MEZCLA no tiene tope: su precio se aleja legitimamente del de la
        // nafta base que tomo como referencia (ver validarMargen).
        Precio precioAplicado = precio;
        if (request.getPrecioUnitario() != null
            && precio.getPrecioUnitario().compareTo(request.getPrecioUnitario()) != 0) {
            precioCatalogo.validarMargen(request.getPrecioUnitario(), precio.getPrecioUnitario(),
                precio.getTipoCombustible(), "precioUnitario");
            precioAplicado = precioCatalogo.reemplazarVigente(
                proveedor, precio.getTipoCombustible(), request.getPrecioUnitario());
        }

        // Origen de la carga: EXACTAMENTE uno de los dos (lo garantiza
        // @OrigenCargaCoherente en el request y el CHECK de la base). Una
        // herramienta no tiene contador: no hay lectura que validar ni
        // operario que actualizar.
        if (vehiculo != null) {
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
            vehiculoConsumo.recalcularConsumo(vehiculo);
            vehiculoRepository.save(vehiculo);
        }

        return toResponse(guardado);
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
            vehiculoConsumo.recalcularUsoAcumulado(vehiculo);
            vehiculoConsumo.recalcularConsumo(vehiculo);
            vehiculoRepository.save(vehiculo);
        }

        // Las fotos NO se borran del storage: son el respaldo del comprobante y
        // la anulacion es reversible.
    }

    /**
     * Analiza la foto de un ticket con el OCR y la resuelve contra el catálogo
     * para pre-cargar el formulario de creación.
     *
     * SIN @Transactional, y eso es EL punto de este método (ver
     * docs/BACKEND-AUDIT.md, TX-01). Antes estaba anotado, así que la llamada
     * HTTP a Mistral —hasta 30 segundos de timeout— corría con una conexión de
     * base tomada y sin hacer nada con ella.
     *
     * Peor todavía: la transacción se abría ANTES del mamparo, así que los
     * requests que esperaban un lugar libre (hasta 5 segundos más) TAMBIÉN
     * retenían una conexión. El semáforo de MistralTicketAnalysisService protege
     * los hilos de Tomcat y la memoria, pero no protegía el pool: con Hikari en
     * 10 conexiones, diez análisis simultáneos dejaban al resto de la API —login,
     * listados, estadísticas— sin conexiones.
     *
     * Ahora la única parte que toca la base es catalogoOcrResolver.resolver(),
     * que abre su propia transacción corta y la cierra. Tiene que vivir en OTRO
     * bean: llamarla desde acá siendo del mismo bean sería una self-invocation y
     * el proxy de Spring no abriría ninguna transacción.
     */
    public TicketAnalysisResponse analyze(MultipartFile ticketFoto) {
        TicketAnalysisService analysisService = analysisProvider.getIfAvailable();
        if (analysisService == null) {
            throw new TicketAnalysisException(ErrorCode.ANALYSIS_UNAVAILABLE,
                "El análisis de tickets no está configurado (falta la API key de Mistral)");
        }

        // Este es el TERCER camino por el que entra un archivo, y el unico que
        // NO pasa por el storage: la foto va derecho a Mistral. Validar solo
        // dentro de MinioStorageService lo dejaria afuera, y este camino manda
        // los bytes a un tercero usando nuestra API key. Se valida antes de
        // llamar al OCR para no gastar un cupo del mamparo en un archivo que ya
        // sabemos que hay que rechazar.
        imageValidator.validar(ticketFoto);

        // Llamada de red, sin transacción abierta.
        var ocr = analysisService.analyze(ticketFoto);

        // Recién acá se toca la base, y por lo que dura resolver tres consultas.
        return catalogoOcrResolver.resolver(ocr);
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
            // Evita el N+1: trae empleado, proveedor, precio, vehiculo y
            // herramienta en la MISMA query (JOIN FETCH). Solo en la query de
            // datos, no en la de count — un fetch en el count rompe. Son todos
            // @ManyToOne (single-valued), así que el fetch join es seguro con
            // paginación.
            //
            // vehiculo y herramienta van con JoinType.LEFT EXPLÍCITO. root.fetch()
            // sin JoinType hace INNER JOIN, y como un ticket tiene vehiculo O
            // herramienta (nunca los dos), un INNER sobre vehiculo BORRA del
            // listado todos los tickets de herramienta. Mismo razonamiento que
            // TicketRepository.findForStats, donde ya está documentado.
            if (query != null && query.getResultType() != Long.class
                    && query.getResultType() != long.class) {
                root.fetch("persona");
                root.fetch("proveedor");
                root.fetch("precio");
                root.fetch("vehiculo", JoinType.LEFT);
                root.fetch("herramienta", JoinType.LEFT);
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

    /**
     * Un ticket con URLs presignadas frescas para sus imagenes.
     *
     * Solo lo ve su DUENO o un ADMINISTRADOR. Sin ese filtro, el id es
     * correlativo y cualquier empleado autenticado podia recorrer 1..N y
     * llevarse los tickets de toda la empresa: montos, proveedores, vehiculos y
     * URLs firmadas de las fotos. listMine() ya filtraba por empleado; este
     * metodo no, y era el agujero.
     *
     * El rechazo es 404 y no 403 a proposito: un 403 confirma que ese ticket
     * existe, y con ids correlativos eso alcanza para contar cuantas cargas
     * tiene la empresa aunque no se pueda leer ninguna. El rechazo no habla.
     */
    @Transactional(readOnly = true)
    public TicketResponse get(Integer id, String username) {
        Persona solicitante = resolvePersona(username);

        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(this::ticketNoEncontrado);

        if (!puedeVer(ticket, solicitante)) {
            throw ticketNoEncontrado();
        }

        return toResponse(ticket);
    }

    /**
     * El administrador ve cualquier ticket (gestiona la flota completa); el
     * empleado, solo los suyos.
     *
     * Se compara por ID y no por username: el username es unico, pero el ID es
     * la identidad real de la fila. Si algun dia el username pasara a ser
     * editable, comparar por texto convertiria un cambio de nombre en un
     * agujero de permisos.
     */
    private boolean puedeVer(Ticket ticket, Persona solicitante) {
        return solicitante.getRol() == Rol.ADMINISTRADOR
            || ticket.getPersona().getId().equals(solicitante.getId());
    }

    private ResourceNotFoundException ticketNoEncontrado() {
        return new ResourceNotFoundException(
            ErrorCode.TICKET_NOT_FOUND, "Ticket no encontrado");
    }

    /**
     * Tickets del empleado autenticado, más recientes primero. Alimenta la
     * pestaña "Historial" de la app del empleado. El empleado sale del JWT, no
     * de un parámetro, para que solo pueda ver los suyos.
     *
     * PAGINADO: el historial crece con cada carga y no se borra nunca, así que
     * devolverlo entero era una lista sin techo sobre la conexión del teléfono.
     * Mismo contrato que listForAdmin (PagedModel).
     *
     * El mapeo a TicketResponse ocurre dentro de la transacción para que las
     * asociaciones LAZY se resuelvan con la sesión abierta.
     */
    @Transactional(readOnly = true)
    public PagedModel<TicketResponse> listMine(String empleadoUsername, Pageable pageable) {
        Persona persona = resolvePersona(empleadoUsername);
        return new PagedModel<>(
            ticketRepository.findVigentesDePersona(persona.getId(), pageable)
                .map(this::toResponse)
        );
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
