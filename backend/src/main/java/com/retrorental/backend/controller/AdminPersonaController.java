package com.retrorental.backend.controller;

import com.retrorental.backend.dto.response.PersonaOpcionResponse;
import com.retrorental.backend.service.PersonaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Listado de personas (empleados y administradores) para poblar filtros de
 * selección en el panel admin.
 *
 * Bajo /admin/**, así que la restricción de rol la aplica SecurityConfig
 * (hasRole("ADMINISTRADOR")); no se declara acá.
 */
@RestController
@RequestMapping("/admin/personas")
@RequiredArgsConstructor
public class AdminPersonaController {

    private final PersonaService personaService;

    @GetMapping
    public ResponseEntity<List<PersonaOpcionResponse>> listar() {
        return ResponseEntity.ok(personaService.listAll());
    }
}
