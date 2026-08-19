package com.retrorental.backend.dto.response;

import com.retrorental.backend.model.enums.TipoCombustible;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Respuesta del endpoint POST /tickets/analyze.
 *
 * Combina lo que leyó el OCR con lo que el backend logró resolver contra el
 * catálogo, para que la app móvil pre-cargue el formulario de creación de
 * ticket. Cualquier campo puede venir null: entonces el empleado lo completa
 * a mano antes de confirmar.
 *
 * - litros / fechaCarga: datos directos del ticket, usables tal cual.
 * - estacion / precioPorLitro / importeTotal: texto/valores crudos del OCR,
 *   útiles para mostrar cuando no hubo match automático.
 * - tipoCombustible: producto clasificado por el OCR. La app lo compara con el
 *   combustible del vehículo para avisar discrepancias. null si ilegible.
 * - idProveedor / proveedorNombre: proveedor de COMBUSTIBLE cuyo nombre coincidió
 *   con "estacion". null si no hubo coincidencia única.
 * - idPrecio / precioUnitario: precio vigente del combustible leído (único por
 *   producto). null si el OCR no leyó el combustible o no hay precio cargado.
 */
public record TicketAnalysisResponse(
    BigDecimal litros,
    LocalDateTime fechaCarga,
    Double importeTotal,
    Double precioPorLitro,
    String estacion,
    TipoCombustible tipoCombustible,
    Integer idProveedor,
    String proveedorNombre,
    Integer idPrecio,
    BigDecimal precioUnitario
) {
}
