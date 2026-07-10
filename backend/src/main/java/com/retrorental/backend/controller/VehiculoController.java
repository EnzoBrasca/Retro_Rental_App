package com.retrorental.backend.controller;

import com.retrorental.backend.dto.response.VehiculoResponse;
import com.retrorental.backend.service.VehiculoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lectura de vehiculos. Sin @RequestMapping a nivel de clase porque agrupa dos
 * rutas con raíces distintas: el catálogo completo (/vehiculos) y el subconjunto
 * asignado al usuario autenticado (/me/vehiculos).
 */
@RestController
@RequiredArgsConstructor
public class VehiculoController {

    private final VehiculoService vehiculoService;

    /**
     * Listado completo de la maquinaria del parque. Accesible a cualquier
     * usuario autenticado (el Jefe lo usa para gestión; el Empleado, como
     * referencia). El ABM (alta/edición/baja) es una tarea aparte.
     */
    @GetMapping("/vehiculos")
    public ResponseEntity<List<VehiculoResponse>> listar() {
        return ResponseEntity.ok(vehiculoService.listAll());
    }

    /**
     * Vehiculos asignados al empleado autenticado. El empleado sale del JWT, no
     * de un parámetro, para que solo pueda ver los suyos. Alimenta el selector
     * de vehiculo del formulario de carga de tickets.
     */
    @GetMapping("/me/vehiculos")
    public ResponseEntity<List<VehiculoResponse>> misVehiculos(Authentication authentication) {
        return ResponseEntity.ok(vehiculoService.listAsignados(authentication.getName()));
    }

    /**
     * El empleado autenticado TOMA un vehiculo libre (self-service). El empleado
     * sale del JWT. Falla si el vehiculo ya está tomado, dado de baja o no
     * disponible.
     */
    @PostMapping("/me/vehiculos/{id}")
    public ResponseEntity<VehiculoResponse> tomar(
            @PathVariable Integer id, Authentication authentication) {
        return ResponseEntity.ok(vehiculoService.tomar(authentication.getName(), id));
    }

    /**
     * El empleado autenticado LIBERA un vehiculo que tiene asignado (baja lógica
     * de la asignación: vuelve a quedar disponible para otro empleado).
     */
    @DeleteMapping("/me/vehiculos/{id}")
    public ResponseEntity<Void> liberar(
            @PathVariable Integer id, Authentication authentication) {
        vehiculoService.liberar(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
