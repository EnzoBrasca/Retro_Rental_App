package com.retrorental.backend.dto.response;

/**
 * Detalle de un error asociado a un campo puntual del request. Se usa dentro de
 * {@link ApiError#errors()} para reportar VARIOS errores de validacion a la vez
 * (uno por cada campo que falla en un @Valid).
 *
 * @param field   nombre del campo (ej. "documento", "telefono.numero")
 * @param code    codigo estable del error de ese campo (ej. "NOT_BLANK", "TOO_SHORT")
 * @param message mensaje legible para mostrar al usuario
 */
public record ApiFieldError(String field, String code, String message) {
}
