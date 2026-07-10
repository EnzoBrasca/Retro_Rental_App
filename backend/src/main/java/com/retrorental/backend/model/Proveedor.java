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

    @Column(name = "cuit", nullable = false)
    private String cuit;

    @Enumerated(EnumType.STRING)
    @Column(name = "servicio", nullable = false)
    private Servicio servicio;

}
