package com.retrorental.backend.model;

import com.retrorental.backend.model.enums.TipoVehiculo;
import com.retrorental.backend.model.enums.TipoCombustible;
import com.retrorental.backend.model.enums.Estado;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
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

    @Column(name = "kilometraje", nullable = false)
    private Integer kilometraje;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_vehiculo", nullable = false)
    private TipoVehiculo tipoVehiculo;

    @Column(name = "consumo_promedio", nullable = false, precision = 10, scale = 2)
    private BigDecimal consumoPromedio;
}