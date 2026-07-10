package com.retrorental.backend.model;

import jakarta.persistence.*;
import com.retrorental.backend.model.enums.Servicio;
import com.retrorental.backend.model.enums.TipoCombustible;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "precios")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Precio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "precio_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioUnitario;

    // Producto cotizado. Solo aplica cuando servicio == COMBUSTIBLE (para
    // REPUESTOS/OTROS queda null). Alimenta el selector de precio del front.
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_combustible")
    private TipoCombustible tipoCombustible;

    @Enumerated(EnumType.STRING)
    @Column(name = "servicio", nullable = false)
    private Servicio servicio;

    // Proveedor que cobra este precio. Con esto el mismo combustible puede tener
    // precios distintos según la estación. Nullable: los precios seedeados sin
    // proveedor y los de REPUESTOS/OTROS lo dejan en null (y así el ALTER de
    // Hibernate en dev no rompe sobre filas existentes).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id")
    private Proveedor proveedor;

    @Column(name = "fecha_desde", nullable = false)
    private LocalDate fechaDesde;

    @Column(name = "fecha_hasta")
    private LocalDate fechaHasta;
}