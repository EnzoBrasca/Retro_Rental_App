package com.retrorental.backend.service;

import com.retrorental.backend.dto.response.PersonaOpcionResponse;
import com.retrorental.backend.repository.PersonaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PersonaService {

    private final PersonaRepository personaRepository;

    /**
     * Listado de todas las personas (empleados y administradores), sin
     * filtrar por rol, para poblar filtros de selección en el panel admin.
     */
    @Transactional(readOnly = true)
    public List<PersonaOpcionResponse> listAll() {
        return personaRepository.findAll().stream()
            .map(persona -> new PersonaOpcionResponse(
                persona.getId(),
                persona.getNombre(),
                persona.getApellido(),
                persona.getUsername(),
                persona.getRol()))
            .toList();
    }
}
