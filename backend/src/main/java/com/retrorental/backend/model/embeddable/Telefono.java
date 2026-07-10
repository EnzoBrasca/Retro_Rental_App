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
public class Telefono {
    
    @Column(name = "codigo_area", length = 5, nullable = false)
    private String codigoArea;

    @Column(name = "numero", length = 15, nullable = false)
    private String telefono;
}