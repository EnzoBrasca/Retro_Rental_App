package com.retrorental.backend.dto.response;

import com.retrorental.backend.model.enums.TipoCombustible;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Datos extraidos por el OCR de Mistral a partir de la foto de un ticket de carga.
 * Cada campo puede venir null si el modelo no logro leerlo con confianza; el
 * front decide entonces si lo deja vacio para que el empleado lo complete.
 */
public record TicketAnalysisResult(
    BigDecimal litros,
    LocalDateTime fechaCarga,
    Double importeTotal,
    Double precioPorLitro,
    String estacion,
    // CUIT del proveedor/estación. Necesario para poder dar de alta el proveedor
    // automáticamente si no existe en el catálogo (columna NOT NULL).
    String cuit,
    // Combustible clasificado por el OCR (uno del enum) o null si ilegible. Con
    // él se resuelve el precio vigente del producto correcto.
    TipoCombustible tipoCombustible
) {
}
