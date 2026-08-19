package com.retrorental.backend.dto.response;

import com.retrorental.backend.model.enums.UnidadUso;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TicketResponse(
    Integer id,
    BigDecimal litros,
    LocalDateTime fechaCarga,
    Integer idPrecio,
    Integer idProveedor,
    // Exactamente uno de idVehiculo/idHerramienta viene con valor: el ticket
    // pertenece a UNO de los dos, nunca a ambos ni a ninguno.
    Integer idVehiculo,
    Integer idHerramienta,
    String empleadoUsername,
    // Lectura del contador al momento de la carga y su unidad (derivada del
    // tipo de vehiculo). null en tickets anteriores a la feature y SIEMPRE
    // null en un ticket de herramienta (no tiene contador ni horometro).
    Integer usoAcumulado,
    UnidadUso unidadUso,
    // Precio por litro efectivamente aplicado a esta carga.
    BigDecimal precioUnitario,
    // Lo persistido: la key del objeto en MinIO.
    String ticketFotoKey,
    // Lo derivado en cada lectura: URL presignada temporal.
    String ticketFotoUrl,
    String tableroFotoKey,
    String tableroFotoUrl,
    // Anulación. null = ticket VIGENTE. Solo aparecen con valor en el listado
    // del admin cuando pide ver los anulados: el resto de las consultas los
    // filtra antes de llegar acá.
    LocalDateTime fechaAnulacion,
    String anuladoPorUsername
) {
}
