package com.retrorental.backend.dto.response;

import java.util.List;

/**
 * Cuerpo estandar de una respuesta de error de la API.
 *
 * @param status  HTTP status numerico (ej. 400, 404, 409)
 * @param code    codigo estable legible por maquina (ej. "DOCUMENTO_ALREADY_EXISTS").
 *                El mobile switchea sobre este valor, NO sobre el texto.
 * @param message mensaje legible para mostrar al usuario (ver services/api.ts)
 * @param field   campo culpable cuando aplica; null si el error es global
 * @param errors  lista por-campo para fallos de validacion (@Valid); null en el
 *                resto de los casos
 */
public record ApiError(
    int status,
    String code,
    String message,
    String field,
    List<ApiFieldError> errors
) {

    // Error de dominio simple (sin campo ni lista): ej. recurso no encontrado.
    public ApiError(int status, String code, String message) {
        this(status, code, message, null, null);
    }

    // Error de dominio apuntando a un campo puntual (ej. identificador duplicado).
    public ApiError(int status, String code, String message, String field) {
        this(status, code, message, field, null);
    }
}
