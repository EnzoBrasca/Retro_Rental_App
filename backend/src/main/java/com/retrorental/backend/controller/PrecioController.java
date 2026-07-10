package com.retrorental.backend.controller;

import com.retrorental.backend.dto.response.PrecioResponse;
import com.retrorental.backend.service.PrecioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lectura de precios vigentes. Accesible a cualquier usuario autenticado (el
 * empleado lo usa como selector al cargar un ticket).
 */
@RestController
@RequestMapping("/precios")
@RequiredArgsConstructor
public class PrecioController {

    private final PrecioService precioService;

    @GetMapping
    public ResponseEntity<List<PrecioResponse>> listar() {
        return ResponseEntity.ok(precioService.listVigentes());
    }
}
