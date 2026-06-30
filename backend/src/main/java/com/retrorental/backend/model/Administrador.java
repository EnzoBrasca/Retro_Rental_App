package com.retrorental.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import java.util.List;

@Entity
@Table(name = "administradores")
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Administrador extends Persona {
    
    @OneToMany(mappedBy = "jefe", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Empleado> empleados;

    @ManyToMany
    @JoinTable(
        name = "administrador_vehiculo",
        joinColumns = @JoinColumn(name = "id_administrador"),
        inverseJoinColumns = @JoinColumn(name = "id_vehiculo")
    )
    private List<Vehiculo> vehiculos;
}