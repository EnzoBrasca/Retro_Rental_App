package com.retrorental.backend.controller;

import com.retrorental.backend.dto.request.CreateVehiculoRequest;
import com.retrorental.backend.dto.request.UpdateVehiculoRequest;
import com.retrorental.backend.dto.response.VehiculoResponse;
import com.retrorental.backend.service.VehiculoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ABM de vehiculos, reservado al ADMINISTRADOR.
 *
 * Bajo /admin/**, así que la restricción de rol la aplica SecurityConfig
 * (hasRole("ADMINISTRADOR")); no se declara acá. El listado público de lectura
 * vive aparte en VehiculoController (GET /vehiculos, /me/vehiculos).
 */
@RestController
@RequestMapping("/admin/vehiculos")
@RequiredArgsConstructor
public class AdminVehiculoController {

    private final VehiculoService vehiculoService;

    /** Listado completo, incluidos los dados de baja (para gestión). */
    @GetMapping
    public ResponseEntity<List<VehiculoResponse>> listar() {
        return ResponseEntity.ok(vehiculoService.listAll());
    }

    /** Alta de un vehiculo. */
    @PostMapping
    public ResponseEntity<VehiculoResponse> crear(@Valid @RequestBody CreateVehiculoRequest request) {
        return ResponseEntity.ok(vehiculoService.create(request));
    }

    /** Edición completa de un vehiculo existente. */
    @PutMapping("/{id}")
    public ResponseEntity<VehiculoResponse> editar(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateVehiculoRequest request) {
        return ResponseEntity.ok(vehiculoService.update(id, request));
    }

    /** Baja lógica (desactivar). No borra la fila: setea fechaBaja. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Integer id) {
        vehiculoService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
