package com.retrorental.backend.exception;

// Un recurso ya existe (email/documento/patente duplicado, baja repetida).
// El HTTP status (409) lo define el ErrorCode que reciba.
public class ConflictException extends AppException {

    public ConflictException(ErrorCode code, String message) {
        super(code, message);
    }

    public ConflictException(ErrorCode code, String message, String field) {
        super(code, message, field);
    }
}
