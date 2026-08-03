package com.retrorental.backend.dto.response;

import java.time.LocalDateTime;

public record TicketResponse(
    Integer id,
    Double litros,
    LocalDateTime fechaCarga,
    Integer idPrecio,
    Integer idProveedor,
    Integer idVehiculo,
    String empleadoUsername,
    // Lo persistido: la key del objeto en MinIO.
    String ticketFotoKey,
    // Lo derivado en cada lectura: URL presignada temporal.
    String ticketFotoUrl,
    String tableroFotoKey,
    String tableroFotoUrl
) {
}
