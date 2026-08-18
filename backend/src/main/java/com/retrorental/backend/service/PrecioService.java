package com.retrorental.backend.service;

import com.retrorental.backend.dto.response.PrecioResponse;
import com.retrorental.backend.model.Precio;
import com.retrorental.backend.repository.PrecioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Lectura de precios vigentes. Alimenta el selector del formulario de carga de
 * tickets. Solo devuelve los precios activos (fechaHasta null).
 */
@Service
@RequiredArgsConstructor
public class PrecioService {

    private final PrecioRepository precioRepository;

    /**
     * Precios vigentes para el selector del formulario de carga.
     *
     * El filtro lo hace la BASE. Antes era `findAll()` + filtro en memoria, o
     * sea que cada apertura del formulario cargaba el historial completo de
     * precios —que solo crece, porque reemplazarVigente cierra las filas viejas
     * en vez de borrarlas— para quedarse con un puñado (ver
     * docs/BACKEND-AUDIT.md, DB-06).
     */
    @Transactional(readOnly = true)
    public List<PrecioResponse> listVigentes() {
        return precioRepository.findByFechaHastaIsNull().stream()
            .map(this::toResponse)
            .toList();
    }

    private PrecioResponse toResponse(Precio p) {
        return new PrecioResponse(
            p.getId(),
            p.getTipoCombustible(),
            p.getPrecioUnitario(),
            p.getServicio(),
            p.getProveedor() != null ? p.getProveedor().getId() : null,
            p.getFechaDesde(),
            p.getFechaHasta()
        );
    }
}
