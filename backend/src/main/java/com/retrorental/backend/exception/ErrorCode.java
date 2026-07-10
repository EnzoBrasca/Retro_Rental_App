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
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT),
    DOCUMENTO_ALREADY_EXISTS(HttpStatus.CONFLICT),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),

    // --- Usuario / autorizacion ---
    USER_NOT_FOUND(HttpStatus.NOT_FOUND),
    NOT_EMPLOYEE(HttpStatus.FORBIDDEN),

    // --- Vehiculos ---
    VEHICULO_NOT_FOUND(HttpStatus.NOT_FOUND),
    PATENTE_ALREADY_EXISTS(HttpStatus.CONFLICT),
    VEHICULO_ALREADY_INACTIVE(HttpStatus.CONFLICT),
    VEHICULO_NOT_ASSIGNED(HttpStatus.FORBIDDEN),
    VEHICULO_ALREADY_ASSIGNED(HttpStatus.CONFLICT),
    VEHICULO_NOT_AVAILABLE(HttpStatus.CONFLICT),

    // --- Tickets ---
    TICKET_NOT_FOUND(HttpStatus.NOT_FOUND),
    PRECIO_NOT_FOUND(HttpStatus.NOT_FOUND),
    PROVEEDOR_NOT_FOUND(HttpStatus.NOT_FOUND),
    // El precio elegido pertenece a otra estación que la del ticket.
    PRECIO_PROVEEDOR_MISMATCH(HttpStatus.CONFLICT),
    ANALYSIS_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    ANALYSIS_FAILED(HttpStatus.SERVICE_UNAVAILABLE),

    // --- Almacenamiento de archivos ---
    FILE_EMPTY(HttpStatus.BAD_REQUEST),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    STORAGE_ERROR(HttpStatus.BAD_GATEWAY),

    // --- Validacion / forma del request ---
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST),

    // --- Seguridad ---
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),

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
