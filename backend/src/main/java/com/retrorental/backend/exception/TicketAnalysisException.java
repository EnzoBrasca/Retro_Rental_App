package com.retrorental.backend.exception;

// Fallo del OCR o servicio de analisis no configurado. El HTTP status (503) lo
// define el ErrorCode que reciba.
public class TicketAnalysisException extends AppException {

    public TicketAnalysisException(ErrorCode code, String message) {
        super(code, message);
    }

    public TicketAnalysisException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
