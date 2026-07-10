package com.retrorental.backend.service;

import com.retrorental.backend.dto.response.TicketAnalysisResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * Extrae los datos de un ticket de carga a partir de su foto, para que el
 * empleado no tenga que cargarlos a mano. La implementacion concreta usa el OCR
 * de Mistral.
 */
public interface TicketAnalysisService {

    // Analiza la foto del ticket y devuelve los campos que logro leer.
    TicketAnalysisResult analyze(MultipartFile ticketFoto);
}
