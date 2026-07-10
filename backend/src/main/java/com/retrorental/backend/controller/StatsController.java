package com.retrorental.backend.controller;

import com.retrorental.backend.dto.response.StatsResponse;
import com.retrorental.backend.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Estadísticas de consumo para el panel del Jefe.
 *
 * Bajo /admin/**, así que ya queda restringido a hasRole("ADMINISTRADOR") por
 * SecurityConfig — no se declara el rol acá. Son datos agregados de TODA la
 * flota, por eso son solo del administrador.
 *
 * El parámetro "fecha" es opcional en los tres endpoints: define a qué día
 * (daily), semana (weekly) o mes (monthly) pertenece el período. Sin él, se usa
 * la fecha actual.
 */
@RestController
@RequestMapping("/admin/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/daily")
    public ResponseEntity<StatsResponse> daily(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(statsService.daily(fecha));
    }

    @GetMapping("/weekly")
    public ResponseEntity<StatsResponse> weekly(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(statsService.weekly(fecha));
    }

    @GetMapping("/monthly")
    public ResponseEntity<StatsResponse> monthly(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(statsService.monthly(fecha));
    }
}
