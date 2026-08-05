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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
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
            // Monto total de la carga (litros x precio unitario), no el precio.
            @RequestParam(required = false) BigDecimal montoMin,
            @RequestParam(required = false) BigDecimal montoMax,
            // Los anulados quedan fuera salvo que se pidan: son la excepcion.
            @RequestParam(defaultValue = "false") boolean incluirAnulados,
            @PageableDefault(size = 20, sort = "fechaCarga", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(ticketService.listForAdmin(
            empleadoId, proveedorId, vehiculoId, desde, hasta,
            montoMin, montoMax, incluirAnulados, pageable));
    }

    /**
     * Anula un ticket. Baja logica: la fila queda (es un registro contable) y
     * se guarda quien la anulo. Ademas revierte en el vehiculo lo que el alta
     * del ticket habia dejado: la lectura del contador y el consumo.
     *
     * Devuelve 204 sin cuerpo. Anular dos veces el mismo ticket da 409.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> anular(@PathVariable Integer id, Authentication auth) {
        ticketService.anular(id, auth.getName());
        return ResponseEntity.noContent().build();
    }
}
