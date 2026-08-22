package com.retrorental.backend.model;

import com.retrorental.backend.model.enums.Servicio;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "proveedores")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Proveedor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    // Nullable SOLO para el proveedor generico, que no identifica a ninguna
    // empresa. Lo garantiza proveedores_cuit_check (ver V14).
    @Column(name = "cuit")
    private String cuit;

    @Enumerated(EnumType.STRING)
    @Column(name = "servicio", nullable = false)
    private Servicio servicio;

    // Marca la fila que agrupa las cargas hechas en estaciones que la empresa
    // no quiere dar de alta como proveedor. Hay exactamente una (ver el indice
    // proveedores_generico_unico en V14).
    //
    // No es un proveedor mas con un nombre distinto: cambia como se resuelve el
    // precio. Los precios son por (proveedor, combustible) con un solo vigente,
    // pero dos cargas de este proveedor son en surtidores distintos, asi que su
    // vigente no aplica a la siguiente carga. Por eso PrecioCatalogoService
    // nunca hereda un precio de un proveedor generico: siempre se tipea.
    @Column(name = "generico", nullable = false)
    private boolean generico;

}
