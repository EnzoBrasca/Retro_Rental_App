package com.retrorental.backend.exception;

// El usuario autenticado no tiene permiso para esta accion. El HTTP status
// (403) lo define el ErrorCode que reciba.
public class ForbiddenException extends AppException {

    public ForbiddenException(ErrorCode code, String message) {
        super(code, message);
    }

    public ForbiddenException(ErrorCode code, String message, String field) {
        super(code, message, field);
    }
}
