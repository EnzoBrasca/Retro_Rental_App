package com.retrorental.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "empleados")
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Empleado extends Persona {
    
    @Column(name = "fecha_alta", nullable = false)
    private LocalDate fechaAlta;

    @Column(name = "fecha_baja")
    private LocalDate fechaBaja;

    // Lado dueño de la relación con el administrador. Se excluye de toString()
    // y equals()/hashCode() porque Administrador tiene la lista inversa
    // (`empleados`): sin esta exclusión, empleado.toString() llama a
    // jefe.toString(), que recorre sus empleados, que vuelven a llamar a
    // jefe.toString() -> recursión infinita y StackOverflowError. Mismo
    // motivo que Vehiculo.operario.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_administrador")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Administrador jefe;

    // Vehiculos asignados a este empleado (lado inverso). El dueño de la
    // relación es Vehiculo.operario (FK id_operario). Un empleado puede tener
    // varios vehiculos; cada vehiculo, un solo operario.
    @OneToMany(mappedBy = "operario", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Vehiculo> vehiculos;
}