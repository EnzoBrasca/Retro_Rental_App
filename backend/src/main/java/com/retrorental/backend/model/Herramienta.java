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

    /**
     * Proporcion nafta:aceite que se asume cuando no se indica una. 50:1 es la
     * mas comun en maquinas 2 tiempos modernas.
     *
     * Vive en el modelo y no en el service porque el invariante es del dominio:
     * una herramienta SIEMPRE tiene una relacion. Inicializar el campo con este
     * valor evita que un objeto recien construido salga con null contra una
     * columna NOT NULL -- el DEFAULT del DDL no lo cubre, porque Hibernate manda
     * el null explicito en el INSERT.
     */
    public static final int RELACION_MEZCLA_DEFAULT = 50;

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

    // Proporcion nafta:aceite de la mezcla que consume esta herramienta, como el
    // primer termino de la relacion: 50 = 50:1 (50 partes de nafta por 1 de
    // aceite). Es una propiedad de la MAQUINA, no de la carga -- una motosierra
    // vieja pide 25:1 y una moderna 50:1 -- por eso vive aca y no en el ticket.
    //
    // Con ella se calcula el precio de la mezcla a partir del de la nafta y el
    // del aceite, en vez de pedirle al empleado un precio de mezcla que no
    // existe en ningun surtidor.
    //
    // NOT NULL con default 50 (la proporcion mas comun en maquinas modernas) a
    // proposito: dejarla nullable haria que la primera carga de mezcla sobre una
    // herramienta ya existente muriera sin relacion, y volver a trabar al
    // operario es justo lo que se acaba de sacar. El admin la corrige por
    // herramienta desde el ABM.
    @Column(name = "relacion_mezcla", nullable = false)
    private Integer relacionMezcla = RELACION_MEZCLA_DEFAULT;

    // Ciclo de vida (baja logica), mismo patron que Vehiculo.fechaBaja. Null =
    // herramienta activa. No se borra fisicamente porque los tickets la
    // referencian.
    @Column(name = "fecha_baja")
    private LocalDate fechaBaja;
}
