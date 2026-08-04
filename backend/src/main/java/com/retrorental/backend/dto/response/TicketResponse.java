package com.retrorental.backend.dto.response;

import com.retrorental.backend.model.enums.UnidadUso;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TicketResponse(
    Integer id,
    Double litros,
    LocalDateTime fechaCarga,
    Integer idPrecio,
    Integer idProveedor,
    Integer idVehiculo,
    String empleadoUsername,
    // Lectura del contador al momento de la carga y su unidad (derivada del
    // tipo de vehiculo). null en tickets anteriores a la feature.
    Integer usoAcumulado,
    UnidadUso unidadUso,
    // Precio por litro efectivamente aplicado a esta carga.
    BigDecimal precioUnitario,
    // Lo persistido: la key del objeto en MinIO.
    String ticketFotoKey,
    // Lo derivado en cada lectura: URL presignada temporal.
    String ticketFotoUrl,
    String tableroFotoKey,
    String tableroFotoUrl
) {
}
