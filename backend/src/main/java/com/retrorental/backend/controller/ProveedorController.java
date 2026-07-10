package com.retrorental.backend.controller;

import com.retrorental.backend.dto.response.ProveedorResponse;
import com.retrorental.backend.service.ProveedorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lectura de proveedores. Accesible a cualquier usuario autenticado (el
 * empleado lo usa como selector al cargar un ticket). Sin @RequestMapping bajo
 * /admin, así que queda cubierto por anyRequest().authenticated().
 */
@RestController
@RequestMapping("/proveedores")
@RequiredArgsConstructor
public class ProveedorController {

    private final ProveedorService proveedorService;

    @GetMapping
    public ResponseEntity<List<ProveedorResponse>> listar() {
        return ResponseEntity.ok(proveedorService.listAll());
    }
}
