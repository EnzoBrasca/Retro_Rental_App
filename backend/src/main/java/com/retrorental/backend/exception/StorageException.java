package com.retrorental.backend.exception;

// Fallo al almacenar/leer archivos (MinIO) o archivo invalido. El HTTP status
// lo define el ErrorCode que reciba (FILE_EMPTY -> 400, STORAGE_ERROR -> 502).
public class StorageException extends AppException {

    public StorageException(ErrorCode code, String message) {
        super(code, message);
    }

    public StorageException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
