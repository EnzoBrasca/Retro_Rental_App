package com.retrorental.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Documento autorizado por el administrador a registrarse via /auth/register.
 *
 * El registro publico sigue siendo self-service: el empleado completa el mismo
 * formulario de siempre. Esta entidad solo define QUIENES pueden hacerlo, para
 * que "publico" no signifique "cualquier persona de internet".
 *
 * Ciclo de vida: el administrador crea la fila con documento + apellido
 * (fechaUso null = habilitado, sin registrar). Cuando el empleado se registra,
 * se marca fechaUso y se guarda la persona creada. Una habilitacion se consume
 * una sola vez.
 */
@Entity
@Table(name = "empleados_habilitados")
@Data
@NoArgsConstructor
public class EmpleadoHabilitado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "documento", nullable = false)
    private String documento;

    /**
     * Se compara contra el apellido que escribe el empleado al registrarse,
     * normalizado. Es el segundo factor del padron: un documento filtrado por
     * si solo no alcanza para crear una cuenta.
     */
    @Column(name = "apellido", nullable = false)
    private String apellido;

    /** Opcional, solo para que el administrador reconozca la fila. No se valida. */
    @Column(name = "nombre")
    private String nombre;

    @Column(name = "fecha_alta", nullable = false)
    private LocalDate fechaAlta;

    /** NULL = habilitado y todavia sin registrar. */
    @Column(name = "fecha_uso")
    private LocalDateTime fechaUso;

    /**
     * Persona creada al consumir la habilitacion. Se guarda para trazabilidad:
     * permite responder quien uso cada autorizacion.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_persona")
    private Persona persona;

    /** Una habilitacion consumida no se puede volver a usar. */
    public boolean estaUsada() {
        return fechaUso != null;
    }
}
