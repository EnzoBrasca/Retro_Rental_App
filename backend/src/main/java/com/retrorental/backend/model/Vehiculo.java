package com.retrorental.backend.model;

import com.retrorental.backend.model.enums.TipoVehiculo;
import com.retrorental.backend.model.enums.TipoCombustible;
import com.retrorental.backend.model.enums.Estado;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
@Entity
@Table(name = "vehiculos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Vehiculo {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String patente;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_combustible", nullable = false)
    private TipoCombustible tipoCombustible;

    @Column(name = "capacidad_tanque", nullable = false)
    private Integer capacidadTanque;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private Estado estado;

    @Column(name = "fecha_ultimo_mantenimiento", nullable = false)
    private LocalDate fechaUltimoMantenimiento;

    // Contador de uso acumulado. La UNIDAD depende del tipo: HORAS para las
    // maquinas viales (horometro), KM para camiones y camionetas (odometro).
    // No se guarda la unidad porque se deriva de tipoVehiculo.unidadUso().
    @Column(name = "uso_acumulado", nullable = false)
    private Integer usoAcumulado;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_vehiculo", nullable = false)
    private TipoVehiculo tipoVehiculo;

    // Consumo sobre TODA la historia de cargas del vehiculo. Lo carga el admin
    // en el alta como estimacion inicial y a partir de la segunda carga lo pisa
    // el calculo real (ver ConsumoCalculator).
    @Column(name = "consumo_promedio", nullable = false, precision = 10, scale = 2)
    private BigDecimal consumoPromedio;

    // Mismo calculo pero solo sobre las ultimas N cargas. Sirve para ver cuando
    // una maquina se desvia de su propio promedio, que suele anticipar una
    // falla. Null mientras no haya dos cargas con lectura.
    @Column(name = "consumo_reciente", precision = 10, scale = 2)
    private BigDecimal consumoReciente;

    // Ciclo de vida (baja lógica), mismo patrón que Empleado.fechaBaja. Null =
    // vehiculo activo. No se borra físicamente porque los tickets lo referencian
    // con FK NOT NULL: desactivar solo setea esta fecha.
    @Column(name = "fecha_baja")
    private LocalDate fechaBaja;

    // Operario actual del vehiculo (lado dueño del OneToMany Empleado→Vehiculo:
    // un vehiculo tiene a lo sumo un operario). Null = libre, disponible para
    // que un empleado lo tome. Se excluye de toString/equals para cortar la
    // recursión con Empleado (ambos usan @Data).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_operario")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Empleado operario;
}