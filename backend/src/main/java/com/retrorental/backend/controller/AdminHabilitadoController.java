package com.retrorental.backend.controller;

import com.retrorental.backend.dto.request.CreateHabilitadoRequest;
import com.retrorental.backend.dto.response.HabilitadoResponse;
import com.retrorental.backend.service.HabilitadoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Padron de empleados habilitados a registrarse, reservado al ADMINISTRADOR.
 *
 * Bajo /admin/**, asi que la restriccion de rol la aplica SecurityConfig
 * (hasRole("ADMINISTRADOR")); no se declara aca.
 *
 * Es la contracara de /auth/register: el registro sigue siendo self-service y
 * sin friccion para el empleado, y este ABM define quienes estan autorizados a
 * usarlo.
 */
@RestController
@RequestMapping("/admin/habilitados")
@RequiredArgsConstructor
@Validated
public class AdminHabilitadoController {

    /**
     * Techo del alta masiva. Es un endpoint que escribe en lote: sin limite,
     * un unico request puede insertar sin techo y convertir una comodidad en
     * una via para llenar la base.
     */
    private static final int MAX_BULK = 500;

    private final HabilitadoService habilitadoService;

    /** Padron completo: habilitados sin registrar y ya registrados. */
    @GetMapping
    public ResponseEntity<List<HabilitadoResponse>> listar() {
        return ResponseEntity.ok(habilitadoService.listAll());
    }

    /** Habilita un documento a registrarse. */
    @PostMapping
    public ResponseEntity<HabilitadoResponse> crear(
            @Valid @RequestBody CreateHabilitadoRequest request) {
        return ResponseEntity.ok(habilitadoService.create(request));
    }

    /**
     * Alta masiva para cargar la nomina de una sola vez. Los documentos ya
     * presentes se saltean en silencio. Devuelve el padron completo.
     */
    @PostMapping("/bulk")
    public ResponseEntity<List<HabilitadoResponse>> crearMasivo(
            @RequestBody
            @NotEmpty(message = "La lista no puede estar vacía")
            @Size(max = MAX_BULK, message = "No se pueden cargar más de 500 documentos por vez")
            List<@Valid CreateHabilitadoRequest> requests) {
        return ResponseEntity.ok(habilitadoService.createBulk(requests));
    }

    /** Quita del padron una habilitacion que todavia no se uso. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        habilitadoService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
