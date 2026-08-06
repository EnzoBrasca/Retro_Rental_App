package com.retrorental.backend.controller;

import com.retrorental.backend.dto.request.CreateHerramientaRequest;
import com.retrorental.backend.dto.request.UpdateHerramientaRequest;
import com.retrorental.backend.dto.response.HerramientaResponse;
import com.retrorental.backend.service.HerramientaService;
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
 * ABM de herramientas, reservado al ADMINISTRADOR.
 *
 * Bajo /admin/**, así que la restricción de rol la aplica SecurityConfig
 * (hasRole("ADMINISTRADOR")); no se declara acá. El listado público de lectura
 * (solo activas) vive aparte en HerramientaController (GET /herramientas).
 */
@RestController
@RequestMapping("/admin/herramientas")
@RequiredArgsConstructor
public class AdminHerramientaController {

    private final HerramientaService herramientaService;

    /** Listado completo, incluidas las dadas de baja (para gestión). */
    @GetMapping
    public ResponseEntity<List<HerramientaResponse>> listar() {
        return ResponseEntity.ok(herramientaService.listAll());
    }

    /** Alta de una herramienta. */
    @PostMapping
    public ResponseEntity<HerramientaResponse> crear(@Valid @RequestBody CreateHerramientaRequest request) {
        return ResponseEntity.ok(herramientaService.create(request));
    }

    /** Edición completa de una herramienta existente. */
    @PutMapping("/{id}")
    public ResponseEntity<HerramientaResponse> editar(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateHerramientaRequest request) {
        return ResponseEntity.ok(herramientaService.update(id, request));
    }

    /** Baja lógica (desactivar). No borra la fila: setea fechaBaja. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Integer id) {
        herramientaService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
