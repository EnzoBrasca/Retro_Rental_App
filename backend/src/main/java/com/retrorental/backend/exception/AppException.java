package com.retrorental.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Base de todas las excepciones de dominio de la app.
 *
 * Cada excepcion carga su {@link ErrorCode} (que a su vez define el HTTP status)
 * y, opcionalmente, el campo culpable. El GlobalExceptionHandler traduce esto a
 * un cuerpo {@code ApiError} con code + message + field, sin necesidad de un
 * handler por cada subclase.
 */
public abstract class AppException extends RuntimeException {

    private final ErrorCode code;
    // Campo del request al que apunta el error (null si es global).
    private final String field;

    protected AppException(ErrorCode code, String message) {
        super(message);
        this.code = code;
        this.field = null;
    }

    protected AppException(ErrorCode code, String message, String field) {
        super(message);
        this.code = code;
        this.field = field;
    }

    protected AppException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.field = null;
    }

    public ErrorCode getCode() {
        return code;
    }

    public String getField() {
        return field;
    }

    public HttpStatus getStatus() {
        return code.getStatus();
    }
}
