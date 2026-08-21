package com.retrorental.backend.service;

import com.retrorental.backend.dto.request.CreateHerramientaRequest;
import com.retrorental.backend.dto.request.UpdateHerramientaRequest;
import com.retrorental.backend.dto.response.HerramientaResponse;
import com.retrorental.backend.exception.ConflictException;
import com.retrorental.backend.exception.ErrorCode;
import com.retrorental.backend.exception.ResourceNotFoundException;
import com.retrorental.backend.model.Herramienta;
import com.retrorental.backend.repository.HerramientaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HerramientaService {

    private final HerramientaRepository herramientaRepository;

    /**
     * Listado de herramientas ACTIVAS (sin fecha de baja). Alimenta el
     * selector del empleado al cargar un ticket: GET /herramientas.
     */
    @Transactional(readOnly = true)
    public List<HerramientaResponse> listActivas() {
        return herramientaRepository.findByFechaBajaIsNull().stream().map(this::toResponse).toList();
    }

    /**
     * Listado completo, incluidas las dadas de baja (para gestion del admin).
     */
    @Transactional(readOnly = true)
    public List<HerramientaResponse> listAll() {
        return herramientaRepository.findAll().stream().map(this::toResponse).toList();
    }

    /** Alta de una herramienta (solo admin). */
    @Transactional
    public HerramientaResponse create(CreateHerramientaRequest request) {
        Herramienta herramienta = new Herramienta();
        herramienta.setNombre(normalizar(request.getNombre()));
        herramienta.setCapacidad(request.getCapacidad());
        herramienta.setRelacionMezcla(
            request.getRelacionMezcla() != null
                ? request.getRelacionMezcla()
                : Herramienta.RELACION_MEZCLA_DEFAULT);
        return toResponse(herramientaRepository.save(herramienta));
    }

    /** Edicion completa de una herramienta (solo admin). */
    @Transactional
    public HerramientaResponse update(Integer id, UpdateHerramientaRequest request) {
        Herramienta herramienta = herramientaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.HERRAMIENTA_NOT_FOUND, "Herramienta no encontrada"));

        herramienta.setNombre(normalizar(request.getNombre()));
        herramienta.setCapacidad(request.getCapacidad());

        // La relacion es el UNICO campo que no se pisa cuando viene ausente. El
        // PUT es reemplazo completo, pero un cliente viejo que la omite estaria
        // borrando en silencio un dato que el admin cargo a mano. Conservar la
        // que estaba es lo unico que no pierde informacion; el default solo
        // entra si la herramienta no tenia ninguna.
        if (request.getRelacionMezcla() != null) {
            herramienta.setRelacionMezcla(request.getRelacionMezcla());
        } else if (herramienta.getRelacionMezcla() == null) {
            herramienta.setRelacionMezcla(Herramienta.RELACION_MEZCLA_DEFAULT);
        }

        return toResponse(herramientaRepository.save(herramienta));
    }

    /**
     * Baja logica de una herramienta (solo admin). No borra la fila: setea
     * fechaBaja, mismo patron que Vehiculo. Idempotencia: si ya estaba dada de
     * baja, se rechaza con 409.
     */
    @Transactional
    public void desactivar(Integer id) {
        Herramienta herramienta = herramientaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.HERRAMIENTA_NOT_FOUND, "Herramienta no encontrada"));
        if (herramienta.getFechaBaja() != null) {
            throw new ConflictException(
                ErrorCode.HERRAMIENTA_ALREADY_INACTIVE, "La herramienta ya está dada de baja");
        }
        herramienta.setFechaBaja(LocalDate.now());
        herramientaRepository.save(herramienta);
    }

    private String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    private HerramientaResponse toResponse(Herramienta herramienta) {
        return new HerramientaResponse(
            herramienta.getId(),
            herramienta.getNombre(),
            herramienta.getCapacidad(),
            herramienta.getRelacionMezcla(),
            herramienta.getFechaBaja()
        );
    }
}
