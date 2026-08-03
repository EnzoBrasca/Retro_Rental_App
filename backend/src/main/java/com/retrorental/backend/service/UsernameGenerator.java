package com.retrorental.backend.service;

import com.retrorental.backend.repository.PersonaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.Normalizer;

/**
 * Genera el username de una persona a partir de nombre + apellido. El
 * username nunca lo elige el usuario: siempre lo autogenera el backend en el
 * alta, y es inmutable de ahí en más (igual criterio que el documento).
 */
@Component
@RequiredArgsConstructor
public class UsernameGenerator {

    private final PersonaRepository personaRepository;

    public String generate(String nombre, String apellido) {
        String base = normalizar(nombre) + normalizar(apellido);
        String candidato = base;
        int sufijo = 2;
        while (personaRepository.existsByUsername(candidato)) {
            candidato = base + sufijo;
            sufijo++;
        }
        return candidato;
    }

    private String normalizar(String s) {
        String sinAcentos = Normalizer.normalize(s, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase().replaceAll("[^a-z]", "");
    }
}
