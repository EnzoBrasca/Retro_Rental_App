package com.retrorental.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Catalogo de codigos de error estables de la API.
 *
 * Cada valor es un identificador legible por maquina (no cambia aunque se
 * reescriba el mensaje en espanol) y lleva asociado su HTTP status. El mobile
 * puede switchear sobre {@code code} sin depender del texto del mensaje.
 *
 * Convencion: MAYUSCULAS_CON_GUION_BAJO, agrupados por dominio.
 */
public enum ErrorCode {

    // --- Autenticacion / registro ---
    DOCUMENTO_ALREADY_EXISTS(HttpStatus.CONFLICT),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    // El documento no esta en el padron de habilitados, el apellido no
    // coincide, o la habilitacion ya fue consumida. Es UN SOLO codigo para los
    // tres casos a proposito: distinguirlos le permitiria a un desconocido
    // averiguar que documentos pertenecen al personal del cliente.
    REGISTRO_NO_HABILITADO(HttpStatus.FORBIDDEN),

    // --- Usuario / autorizacion ---
    USER_NOT_FOUND(HttpStatus.NOT_FOUND),
    NOT_EMPLOYEE(HttpStatus.FORBIDDEN),

    // --- Vehiculos ---
    VEHICULO_NOT_FOUND(HttpStatus.NOT_FOUND),
    // Ya hay otro vehiculo con ese identificador (patente o numero interno).
    IDENTIFICADOR_ALREADY_EXISTS(HttpStatus.CONFLICT),
    VEHICULO_ALREADY_INACTIVE(HttpStatus.CONFLICT),
    VEHICULO_NOT_ASSIGNED(HttpStatus.FORBIDDEN),
    VEHICULO_ALREADY_ASSIGNED(HttpStatus.CONFLICT),
    VEHICULO_NOT_AVAILABLE(HttpStatus.CONFLICT),

    // --- Herramientas ---
    HERRAMIENTA_NOT_FOUND(HttpStatus.NOT_FOUND),
    HERRAMIENTA_ALREADY_INACTIVE(HttpStatus.CONFLICT),

    // --- Empleados ---
    EMPLEADO_NOT_FOUND(HttpStatus.NOT_FOUND),
    EMPLEADO_ALREADY_INACTIVE(HttpStatus.CONFLICT),

    // --- Padron de habilitados a registrarse ---
    HABILITADO_NOT_FOUND(HttpStatus.NOT_FOUND),
    HABILITADO_ALREADY_EXISTS(HttpStatus.CONFLICT),
    // Se intento quitar del padron una habilitacion ya consumida. La cuenta ya
    // existe: para sacarle el acceso hay que dar de baja al empleado, no
    // borrar la fila del padron.
    HABILITADO_ALREADY_USED(HttpStatus.CONFLICT),

    // --- Tickets ---
    TICKET_NOT_FOUND(HttpStatus.NOT_FOUND),
    // Se intento anular un ticket que ya estaba anulado.
    TICKET_ALREADY_ANULADO(HttpStatus.CONFLICT),
    PRECIO_NOT_FOUND(HttpStatus.NOT_FOUND),
    PROVEEDOR_NOT_FOUND(HttpStatus.NOT_FOUND),
    // El precio elegido pertenece a otra estación que la del ticket.
    PRECIO_PROVEEDOR_MISMATCH(HttpStatus.CONFLICT),
    // La lectura del contador es menor que la ultima registrada del vehiculo.
    // Un odometro/horometro no retrocede: casi siempre es un error de tipeo.
    USO_ACUMULADO_RETROCEDE(HttpStatus.CONFLICT),
    // El precio corregido a mano se aleja demasiado del vigente (ver
    // app.precio.margen-maximo). Freno contra ceros de mas y OCR alucinado.
    PRECIO_FUERA_DE_RANGO(HttpStatus.CONFLICT),
    // Una carga de herramienta pidio un tipoCombustible sin precio vigente
    // para ese proveedor y no es MEZCLA (unico caso que se auto-crea): no hay
    // de donde sacar el precio, no se inventa.
    PRECIO_NOT_FOUND_PARA_COMBUSTIBLE(HttpStatus.NOT_FOUND),
    // MEZCLA sin precio vigente en el proveedor se crea copiando el vigente de
    // NAFTA_SUPER de ESE proveedor (ver TicketService.resolvePrecioPorCombustible).
    // Si el proveedor tampoco tiene NAFTA_SUPER, no hay base de la que copiar.
    PRECIO_BASE_MEZCLA_NOT_FOUND(HttpStatus.NOT_FOUND),
    ANALYSIS_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    ANALYSIS_FAILED(HttpStatus.SERVICE_UNAVAILABLE),
    // El OCR esta saturado: se alcanzo el tope de analisis simultaneos (ver
    // mistral.max-concurrent). Es distinto de ANALYSIS_UNAVAILABLE, que
    // significa "no configurado": aca reintentar en un rato SI sirve, y el
    // mobile puede decir cosas distintas en cada caso.
    ANALYSIS_BUSY(HttpStatus.SERVICE_UNAVAILABLE),

    // --- Almacenamiento de archivos ---
    FILE_EMPTY(HttpStatus.BAD_REQUEST),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    // El archivo no es una de las imagenes permitidas. Se decide por los magic
    // bytes del contenido, NO por el Content-Type del request (ese lo escribe
    // el cliente y no significa nada). Ver ImageValidator.
    FILE_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST),
    STORAGE_ERROR(HttpStatus.BAD_GATEWAY),

    // --- Validacion / forma del request ---
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST),

    // --- Seguridad ---
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    // Demasiados intentos de login desde la misma IP (ver LoginRateLimitFilter).
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS),

    // --- Generico ---
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
