package com.retrorental.backend.service;

import com.retrorental.backend.dto.response.ProveedorResponse;
import com.retrorental.backend.model.Proveedor;
import com.retrorental.backend.repository.ProveedorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Lectura de proveedores (estaciones de servicio). Alimenta el selector del
 * formulario de carga de tickets. El ABM de proveedores es una tarea aparte.
 */
@Service
@RequiredArgsConstructor
public class ProveedorService {

    private final ProveedorRepository proveedorRepository;

    @Transactional(readOnly = true)
    public List<ProveedorResponse> listAll() {
        return proveedorRepository.findAll().stream()
            // El generico va ultimo en el selector: es el descarte para cuando
            // ninguna de las estaciones de arriba es la correcta, no una opcion
            // mas en igualdad de condiciones. Boolean ordena false antes que true.
            .sorted(Comparator.comparing(Proveedor::isGenerico))
            .map(this::toResponse)
            .toList();
    }

    private ProveedorResponse toResponse(Proveedor p) {
        return new ProveedorResponse(
            p.getId(), p.getNombre(), p.getCuit(), p.getServicio(), p.isGenerico());
    }
}
