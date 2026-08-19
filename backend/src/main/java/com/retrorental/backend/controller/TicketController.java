package com.retrorental.backend.controller;

import com.retrorental.backend.dto.request.CreateTicketRequest;
import com.retrorental.backend.dto.response.TicketAnalysisResponse;
import com.retrorental.backend.dto.response.TicketResponse;
import com.retrorental.backend.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    // Crea un ticket con sus dos fotos. El empleado sale del JWT, no del request.
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TicketResponse> create(
            @Valid @ModelAttribute CreateTicketRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(ticketService.create(request, authentication.getName()));
    }

    // Analiza la foto de un ticket con OCR y devuelve los campos pre-cargados
    // (sin persistir). El empleado revisa/completa antes de crear el ticket.
    @PostMapping(path = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TicketAnalysisResponse> analyze(
            @RequestParam("ticketFoto") MultipartFile ticketFoto) {
        return ResponseEntity.ok(ticketService.analyze(ticketFoto));
    }

    /**
     * Historial del empleado autenticado: sus tickets, más recientes primero.
     * El empleado sale del JWT, no de un parámetro, para que solo vea los suyos.
     *
     * PAGINADO, mismo contrato que GET /admin/tickets. El historial crece con
     * cada carga y no se borra nunca: devolverlo entero no tenía techo.
     *
     * Ejemplo: GET /tickets/me?page=0&size=20
     */
    @GetMapping("/me")
    public ResponseEntity<PagedModel<TicketResponse>> misTickets(
            Authentication authentication,
            @PageableDefault(size = 20, sort = "fechaCarga", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(ticketService.listMine(authentication.getName(), pageable));
    }

    // Devuelve un ticket con URLs presignadas frescas para sus imagenes.
    // El solicitante sale del JWT: solo el dueño del ticket o un administrador
    // pueden verlo (lo resuelve el service). Sin eso, el id es correlativo y
    // cualquier empleado podia recorrerlos todos.
    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> get(
            @PathVariable Integer id, Authentication authentication) {
        return ResponseEntity.ok(ticketService.get(id, authentication.getName()));
    }
}
