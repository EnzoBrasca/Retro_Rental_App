package com.retrorental.backend.exception;

// Un recurso referenciado no existe. El HTTP status (404) lo define el
// ErrorCode que reciba.
public class ResourceNotFoundException extends AppException {

    public ResourceNotFoundException(ErrorCode code, String message) {
        super(code, message);
    }

    public ResourceNotFoundException(ErrorCode code, String message, String field) {
        super(code, message, field);
    }
}
