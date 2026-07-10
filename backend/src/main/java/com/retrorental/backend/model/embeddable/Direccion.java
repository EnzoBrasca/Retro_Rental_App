package com.retrorental.backend.model.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Direccion {
    
    @Column(name = "calle", length = 150, nullable = false)
    private String calle;

    @Column(name = "numero", length = 20, nullable = false)
    private String numero;

    @Column(name = "ciudad", length = 100, nullable = false)
    private String ciudad;

    @Column(name = "provincia", length = 100, nullable = false)
    private String provincia;

    @Column(name = "codigo_postal", length = 10, nullable = false)
    private String codigoPostal;

    @Column(name = "barrio", length = 20, nullable = false)
    private String barrio;
}