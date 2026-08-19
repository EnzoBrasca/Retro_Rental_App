package com.retrorental.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.util.List;

@Entity
@Table(name = "administradores")
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Administrador extends Persona {

    // Lado inverso de Empleado.jefe. Excluido de toString()/equals()/hashCode()
    // por dos motivos: cierra el ciclo bidireccional con Empleado (recursión
    // infinita -> StackOverflowError) y, al ser LAZY por defecto en @OneToMany,
    // tocarlo fuera de una transacción abierta lanzaría LazyInitializationException.
    @OneToMany(mappedBy = "jefe", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Empleado> empleados;

    // Mismo criterio: colección LAZY que no aporta identidad al administrador.
    @ManyToMany
    @JoinTable(
        name = "administrador_vehiculo",
        joinColumns = @JoinColumn(name = "id_administrador"),
        inverseJoinColumns = @JoinColumn(name = "id_vehiculo")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Vehiculo> vehiculos;
}