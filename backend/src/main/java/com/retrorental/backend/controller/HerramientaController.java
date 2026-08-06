package com.retrorental.backend.controller;

import com.retrorental.backend.dto.response.HerramientaResponse;
import com.retrorental.backend.service.HerramientaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lectura de herramientas para el empleado. Solo las ACTIVAS (sin fecha de
 * baja): alimenta el selector de origen al cargar un ticket. El ABM
 * (alta/edicion/baja) vive en AdminHerramientaController, bajo /admin.
 */
@RestController
@RequiredArgsConstructor
public class HerramientaController {

    private final HerramientaService herramientaService;

    @GetMapping("/herramientas")
    public ResponseEntity<List<HerramientaResponse>> listar() {
        return ResponseEntity.ok(herramientaService.listActivas());
    }
}
