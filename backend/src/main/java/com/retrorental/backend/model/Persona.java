package com.retrorental.backend.model;

import com.retrorental.backend.model.enums.Rol;
import com.retrorental.backend.model.embeddable.Direccion;
import com.retrorental.backend.model.embeddable.Telefono;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "personas")
@Inheritance(strategy = InheritanceType.JOINED)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Persona {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "apellido", nullable = false)
    private String apellido;

    @Column(name = "documento", nullable = false)
    private String documento;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false)
    private Rol rol;

    @Embedded
    @AttributeOverride(name = "numero", column = @Column(name = "direccion_numero"))
    private Direccion direccion;

    @Embedded
    @AttributeOverride(name = "telefono", column = @Column(name = "telefono_numero"))
    private Telefono telefono;
}