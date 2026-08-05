package com.retrorental.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "litros", nullable = false)
    private Double litros;

    @Column(name = "fecha_carga", nullable = false)
    private LocalDateTime fechaCarga;

    // Lectura del contador del vehiculo al momento de la carga. La unidad la
    // define el vehiculo (horas en una maquina, km en el resto). Nullable solo
    // por los tickets anteriores a la feature: los nuevos siempre la traen.
    @Column(name = "uso_acumulado")
    private Integer usoAcumulado;

    @Column(name = "ticket_foto_url")
    private String ticketFotoUrl;

    @Column(name = "tablero_foto_url")
    private String tableroFotoUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_precio", nullable = false)
    private Precio precio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_proveedor", nullable = false)
    private Proveedor proveedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_persona", nullable = false)
    private Persona persona;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_vehiculo", nullable = false)
    private Vehiculo vehiculo;

    // Anulacion (baja logica). Null = ticket VIGENTE. Un ticket es un registro
    // contable: no se borra, se anula. Las consultas de consumo, estadisticas e
    // historial filtran por esto; ver TicketRepository.
    @Column(name = "fecha_anulacion")
    private LocalDateTime fechaAnulacion;

    // Quien lo anulo. Se excluye del fetch por defecto porque solo lo mira el
    // listado del admin cuando pide ver los anulados.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anulado_por")
    private Persona anuladoPor;

    public boolean estaAnulado() {
        return fechaAnulacion != null;
    }
}
