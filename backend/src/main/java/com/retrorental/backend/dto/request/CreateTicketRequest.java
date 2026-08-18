package com.retrorental.backend.dto.request;

import com.retrorental.backend.model.enums.TipoCombustible;
import com.retrorental.backend.validation.CargaOrigen;
import com.retrorental.backend.validation.OrigenCargaCoherente;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@OrigenCargaCoherente
public class CreateTicketRequest implements CargaOrigen {

    // BigDecimal desde el BORDE de entrada, no convertido mas adentro: si se
    // recibiera como Double, el valor ya llegaria con error de representacion y
    // convertirlo despues no lo recupera (ver docs/BACKEND-AUDIT.md, DB-04).
    // Jackson parsea el numero del JSON directo a BigDecimal, exacto.
    @NotNull(message = "Los litros son obligatorios")
    @Positive(message = "Los litros deben ser mayores a cero")
    private BigDecimal litros;

    // Opcional: si no viene, el backend usa el momento actual. No puede ser futura.
    @PastOrPresent(message = "La fecha de carga no puede ser futura")
    private LocalDateTime fechaCarga;

    // Precio del catalogo ya resuelto. Obligatorio SOLO cuando el origen es un
    // vehiculo (lo exige @OrigenCargaCoherente, no un @NotNull de campo): una
    // herramienta no lo manda, manda tipoCombustible en su lugar y el service
    // lo resuelve (ver TicketService.resolvePrecioPorCombustible).
    @Positive(message = "El id de precio debe ser válido")
    private Integer idPrecio;

    // Combustible elegido para ESTA carga. Obligatorio SOLO cuando el origen es
    // una herramienta (una herramienta no tiene combustible fijo, a diferencia
    // de un vehiculo, que ya lo trae resuelto en su idPrecio). Lo exige
    // @OrigenCargaCoherente.
    private TipoCombustible tipoCombustible;

    // Precio por litro REALMENTE pagado. Opcional: si no viene, vale el del
    // catalogo (idPrecio). Si viene y difiere, el service valida que no se aleje
    // mas de app.precio.margen-maximo del vigente y actualiza el catalogo.
    @Positive(message = "El precio por litro debe ser mayor a cero")
    private BigDecimal precioUnitario;

    // Lectura del odometro/horometro del vehiculo al momento de la carga.
    // Obligatoria SOLO cuando el origen es un vehiculo (una herramienta no
    // tiene contador ni horometro): lo exige @OrigenCargaCoherente, no un
    // @NotNull de campo, porque la obligatoriedad depende de idVehiculo/
    // idHerramienta.
    @PositiveOrZero(message = "La lectura del contador no puede ser negativa")
    private Integer usoAcumulado;

    @NotNull(message = "El proveedor es obligatorio")
    @Positive(message = "El id de proveedor debe ser válido")
    private Integer idProveedor;

    // Vehiculo que se está cargando. Exclusivo con idHerramienta: exactamente
    // uno de los dos debe venir (lo exige @OrigenCargaCoherente). Debe ser uno
    // de los asignados al empleado (se valida en el service).
    @Positive(message = "El id de vehiculo debe ser válido")
    private Integer idVehiculo;

    // Herramienta que se está cargando (motosierra, bidon, etc.). Exclusivo
    // con idVehiculo.
    @Positive(message = "El id de herramienta debe ser válido")
    private Integer idHerramienta;

    // Foto del ticket de carga (parte multipart "ticketFoto"). OPCIONAL: el
    // empleado puede registrar la carga sin comprobante (equipos de gama baja /
    // baja alfabetización digital). Si viene, el StorageService valida que no
    // esté vacía al subirla.
    private MultipartFile ticketFoto;

    // Foto del tablero del vehiculo (parte multipart "tableroFoto"). OPCIONAL:
    // la app mobile actual solo captura la foto del ticket; la del tablero se
    // planea para una versión futura. Si no viene, el ticket se crea sin ella.
    private MultipartFile tableroFoto;
}
