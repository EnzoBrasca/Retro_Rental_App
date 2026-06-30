package com.retrorental.backend.service;

import com.retrorental.backend.dto.request.LoginRequest;
import com.retrorental.backend.dto.request.RegisterRequest;
import com.retrorental.backend.dto.response.AuthResponse;
import com.retrorental.backend.model.Administrador;
import com.retrorental.backend.model.Empleado;
import com.retrorental.backend.model.Persona;
import com.retrorental.backend.model.enums.Rol;
import com.retrorental.backend.repository.PersonaRepository;
import com.retrorental.backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final PersonaRepository personaRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthResponse register(RegisterRequest request) {

        if (personaRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Ya existe un usuario con ese email");
        }

        if (personaRepository.existsByDocumento(request.getDocumento())) {
            throw new RuntimeException("Ya existe un usuario con ese documento");
        }

        Persona persona;

        if (request.getRol() == Rol.ADMINISTRADOR) {
            persona = new Administrador();
        } else {
            Empleado empleado = new Empleado();
            empleado.setFechaAlta(LocalDate.now());
            persona = empleado;
        }

        persona.setNombre(request.getNombre());
        persona.setApellido(request.getApellido());
        persona.setDocumento(request.getDocumento());
        persona.setEmail(request.getEmail());
        persona.setPassword(passwordEncoder.encode(request.getPassword()));
        persona.setRol(request.getRol());

        personaRepository.save(persona);

        String token = jwtUtil.generateToken(persona.getEmail(), persona.getRol().name());

        return new AuthResponse(
            token,
            persona.getNombre(),
            persona.getApellido(),
            persona.getEmail(),
            persona.getRol()
        );
    }

    public AuthResponse login(LoginRequest request) {

        Persona persona = personaRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new RuntimeException("Email o contraseña incorrectos"));

        if (!passwordEncoder.matches(request.getPassword(), persona.getPassword())) {
            throw new RuntimeException("Email o contraseña incorrectos");
        }

        String token = jwtUtil.generateToken(persona.getEmail(), persona.getRol().name());

        return new AuthResponse(
            token,
            persona.getNombre(),
            persona.getApellido(),
            persona.getEmail(),
            persona.getRol()
        );
    }
}