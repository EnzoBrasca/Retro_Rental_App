package com.retrorental.backend.controller;

import com.retrorental.backend.dto.response.TicketResponse;
import com.retrorental.backend.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * Endpoints de tickets reservados al ADMINISTRADOR.
 *
 * La restricción de rol NO se declara aquí: el prefijo /admin/** ya está
 * limitado a hasRole("ADMINISTRADOR") en SecurityConfig, así que cualquier
 * controller bajo /admin queda protegido automáticamente.
 */
@RestController
@RequestMapping("/admin/tickets")
@RequiredArgsConstructor
public class AdminTicketController {

    private final TicketService ticketService;

    /**
     * Listado paginado de tickets ya creados. Todos los filtros son opcionales
     * y se combinan (AND). Sin filtros devuelve todos, ordenados por fecha de
     * carga descendente (lo más reciente primero).
     *
     * Ejemplo: GET /admin/tickets?proveedorId=3&desde=2026-07-01T00:00:00&page=0&size=20
     */
    @GetMapping
    public ResponseEntity<PagedModel<TicketResponse>> list(
            @RequestParam(required = false) Integer empleadoId,
            @RequestParam(required = false) Integer proveedorId,
            @RequestParam(required = false) Integer vehiculoId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @PageableDefault(size = 20, sort = "fechaCarga", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(
            ticketService.listForAdmin(empleadoId, proveedorId, vehiculoId, desde, hasta, pageable));
    }
}
