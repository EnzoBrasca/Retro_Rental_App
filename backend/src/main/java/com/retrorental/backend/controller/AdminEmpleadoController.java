package com.retrorental.backend.controller;

import com.retrorental.backend.dto.request.CreateEmpleadoRequest;
import com.retrorental.backend.dto.request.UpdateEmpleadoRequest;
import com.retrorental.backend.dto.response.EmpleadoResponse;
import com.retrorental.backend.service.EmpleadoService;
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
 * ABM de empleados (gestión de personal), reservado al ADMINISTRADOR.
 *
 * Bajo /admin/**, así que la restricción de rol la aplica SecurityConfig
 * (hasRole("ADMINISTRADOR")); no se declara acá.
 */
@RestController
@RequestMapping("/admin/empleados")
@RequiredArgsConstructor
public class AdminEmpleadoController {

    private final EmpleadoService empleadoService;

    /** Listado completo del personal, incluidos los dados de baja (para gestión). */
    @GetMapping
    public ResponseEntity<List<EmpleadoResponse>> listar() {
        return ResponseEntity.ok(empleadoService.listAll());
    }

    /** Alta de un empleado. */
    @PostMapping
    public ResponseEntity<EmpleadoResponse> crear(@Valid @RequestBody CreateEmpleadoRequest request) {
        return ResponseEntity.ok(empleadoService.create(request));
    }

    /** Edición completa de un empleado existente. */
    @PutMapping("/{id}")
    public ResponseEntity<EmpleadoResponse> editar(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateEmpleadoRequest request) {
        return ResponseEntity.ok(empleadoService.update(id, request));
    }

    /** Baja lógica (desactivar). No borra la fila: setea fechaBaja. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Integer id) {
        empleadoService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
