package com.retrorental.backend.exception;

import com.retrorental.backend.dto.response.ApiError;
import com.retrorental.backend.dto.response.ApiFieldError;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * Traduce las excepciones de la app a respuestas HTTP con cuerpo JSON
 * {@link ApiError} (code + message + field + errors).
 *
 * Sin este handler, cualquier excepcion en un controller provoca un forward
 * interno a /error; como /error no esta en la lista de rutas publicas de
 * SecurityConfig, Spring Security lo bloquea con 403, enmascarando el error
 * real. Manejar la excepcion aca la resuelve en el mismo dispatch y evita ese
 * 403 espurio.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Mapea el codigo de la anotacion de Bean Validation (NotBlank, Email, ...)
    // a un codigo estable por-campo que consume el mobile.
    private static final Map<String, String> FIELD_CODES = Map.ofEntries(
        Map.entry("NotBlank", "REQUIRED"),
        Map.entry("NotNull", "REQUIRED"),
        Map.entry("NotEmpty", "REQUIRED"),
        Map.entry("Email", "INVALID_FORMAT"),
        Map.entry("Pattern", "INVALID_FORMAT"),
        Map.entry("Size", "INVALID_LENGTH"),
        Map.entry("Length", "INVALID_LENGTH"),
        Map.entry("Positive", "OUT_OF_RANGE"),
        Map.entry("PositiveOrZero", "OUT_OF_RANGE"),
        Map.entry("Negative", "OUT_OF_RANGE"),
        Map.entry("Min", "OUT_OF_RANGE"),
        Map.entry("Max", "OUT_OF_RANGE"),
        Map.entry("DecimalMin", "OUT_OF_RANGE"),
        Map.entry("DecimalMax", "OUT_OF_RANGE"),
        Map.entry("Past", "INVALID_DATE"),
        Map.entry("PastOrPresent", "INVALID_DATE"),
        Map.entry("Future", "INVALID_DATE")
    );

    // ---------------------------------------------------------------------
    // Excepciones de dominio: un solo handler para todas (cada una carga su
    // ErrorCode, que define el HTTP status, y opcionalmente el campo).
    // ---------------------------------------------------------------------
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiError> handleApp(AppException ex) {
        ErrorCode code = ex.getCode();
        return ResponseEntity.status(code.getStatus()).body(
            new ApiError(code.getStatus().value(), code.name(), ex.getMessage(), ex.getField()));
    }

    // ---------------------------------------------------------------------
    // Validacion de @Valid: cubre @RequestBody (MethodArgumentNotValidException)
    // y @ModelAttribute multipart (BindException), porque la primera extiende a
    // la segunda. Devuelve la lista completa de campos que fallaron.
    // ---------------------------------------------------------------------
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiError> handleValidation(BindException ex) {
        List<ApiFieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .map(this::toFieldError)
            .toList();

        String message = fieldErrors.stream()
            .map(ApiFieldError::message)
            .reduce((a, b) -> a + "; " + b)
            .orElse("Datos invalidos");
        String field = fieldErrors.isEmpty() ? null : fieldErrors.get(0).field();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(
            HttpStatus.BAD_REQUEST.value(),
            ErrorCode.VALIDATION_ERROR.name(),
            message,
            field,
            fieldErrors));
    }

    private ApiFieldError toFieldError(FieldError fe) {
        String annotation = fe.getCode(); // "NotBlank", "Email", "Size", ...
        String code = FIELD_CODES.getOrDefault(annotation, "INVALID");
        return new ApiFieldError(fe.getField(), code, fe.getDefaultMessage());
    }

    // ---------------------------------------------------------------------
    // Cuerpo JSON ilegible: malformado, vacio, o enum inexistente en el body
    // (ej. rol: "JEFE"). Jackson no puede deserializar -> 400.
    // ---------------------------------------------------------------------
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(ErrorCode.MALFORMED_REQUEST,
            "El cuerpo de la peticion es invalido o esta mal formado", null);
    }

    // ---------------------------------------------------------------------
    // Tipo de parametro/path incorrecto: /tickets/abc (id no numerico), fecha
    // mal formada, enum invalido en query param -> 400.
    // ---------------------------------------------------------------------
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "El valor de '%s' no es valido".formatted(ex.getName());
        return build(ErrorCode.INVALID_PARAMETER, message, ex.getName());
    }

    // ---------------------------------------------------------------------
    // Falta un parametro requerido (@RequestParam sin default): ej. "key",
    // "file", "ticketFoto" -> 400.
    // ---------------------------------------------------------------------
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex) {
        String message = "Falta el parametro requerido '%s'".formatted(ex.getParameterName());
        return build(ErrorCode.MISSING_PARAMETER, message, ex.getParameterName());
    }

    // Falta una parte multipart requerida (ej. la foto del ticket) -> 400.
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> handleMissingPart(MissingServletRequestPartException ex) {
        String message = "Falta el archivo requerido '%s'".formatted(ex.getRequestPartName());
        return build(ErrorCode.MISSING_PARAMETER, message, ex.getRequestPartName());
    }

    // ---------------------------------------------------------------------
    // Archivo mas grande que el limite configurado -> 413.
    // ---------------------------------------------------------------------
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleTooLarge(MaxUploadSizeExceededException ex) {
        return build(ErrorCode.FILE_TOO_LARGE,
            "El archivo supera el tamano maximo permitido", null);
    }

    // Otros fallos de multipart (request mal formado) -> 400.
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiError> handleMultipart(MultipartException ex) {
        return build(ErrorCode.MALFORMED_REQUEST,
            "La peticion multipart es invalida", null);
    }

    // ---------------------------------------------------------------------
    // Acceso denegado a nivel de metodo (@PreAuthorize). Los denies por URL
    // (/admin/**) los maneja el filtro de seguridad en SecurityConfig.
    // ---------------------------------------------------------------------
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return build(ErrorCode.ACCESS_DENIED,
            "No tenes permiso para realizar esta accion", null);
    }

    // ---------------------------------------------------------------------
    // Red de seguridad: cualquier excepcion no contemplada -> 500 controlado.
    // Se loguea el detalle real y se devuelve un mensaje generico (no filtra
    // internals al cliente).
    // ---------------------------------------------------------------------
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Error no controlado", ex);
        return build(ErrorCode.INTERNAL_ERROR,
            "Ocurrio un error inesperado. Intenta nuevamente mas tarde", null);
    }

    private ResponseEntity<ApiError> build(ErrorCode code, String message, String field) {
        return ResponseEntity.status(code.getStatus()).body(
            new ApiError(code.getStatus().value(), code.name(), message, field));
    }
}
