package com.retrorental.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Herramienta a motor sin patente ni horometro (motosierra, bidones, etc.).
 *
 * A proposito NO es un Vehiculo ni comparte tabla con el: no tiene
 * identificador, modelo, tipo, uso acumulado ni consumo promedio, y el
 * combustible se elige carga por carga (ver Ticket.herramienta), no es fijo
 * como en un vehiculo. Solo tiene nombre y capacidad.
 */
@Entity
@Table(name = "herramientas")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Herramienta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String nombre;

    // Capacidad del tanque/recipiente. BigDecimal porque una motosierra puede
    // tener un tanque fraccionario (ej. 0.3 litros), a diferencia del tanque
    // entero de un vehiculo.
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal capacidad;

    // Ciclo de vida (baja logica), mismo patron que Vehiculo.fechaBaja. Null =
    // herramienta activa. No se borra fisicamente porque los tickets la
    // referencian.
    @Column(name = "fecha_baja")
    private LocalDate fechaBaja;
}
