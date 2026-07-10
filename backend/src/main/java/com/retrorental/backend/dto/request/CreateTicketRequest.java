package com.retrorental.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Data
public class CreateTicketRequest {

    @NotNull(message = "Los litros son obligatorios")
    @Positive(message = "Los litros deben ser mayores a cero")
    private Double litros;

    // Opcional: si no viene, el backend usa el momento actual. No puede ser futura.
    @PastOrPresent(message = "La fecha de carga no puede ser futura")
    private LocalDateTime fechaCarga;

    @NotNull(message = "El precio es obligatorio")
    @Positive(message = "El id de precio debe ser válido")
    private Integer idPrecio;

    @NotNull(message = "El proveedor es obligatorio")
    @Positive(message = "El id de proveedor debe ser válido")
    private Integer idProveedor;

    // Vehiculo que se está cargando. Debe ser uno de los asignados al empleado
    // (se valida en el service contra su lista de vehiculos).
    @NotNull(message = "El vehiculo es obligatorio")
    @Positive(message = "El id de vehiculo debe ser válido")
    private Integer idVehiculo;

    // Foto del ticket de carga (parte multipart "ticketFoto"). El contenido no
    // vacío se valida en el StorageService al subirla.
    @NotNull(message = "La foto del ticket es obligatoria")
    private MultipartFile ticketFoto;

    // Foto del tablero del vehiculo (parte multipart "tableroFoto"). OPCIONAL:
    // la app mobile actual solo captura la foto del ticket; la del tablero se
    // planea para una versión futura. Si no viene, el ticket se crea sin ella.
    private MultipartFile tableroFoto;
}
