package com.retrorental.backend.exception;

// Credenciales de login incorrectas. El HTTP status (401) lo define el
// ErrorCode que reciba (INVALID_CREDENTIALS).
public class InvalidCredentialsException extends AppException {

    public InvalidCredentialsException(String message) {
        super(ErrorCode.INVALID_CREDENTIALS, message);
    }
}
