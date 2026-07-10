package com.retrorental.backend.service;

import com.retrorental.backend.dto.request.LoginRequest;
import com.retrorental.backend.dto.request.RegisterRequest;
import com.retrorental.backend.dto.response.AuthResponse;
import com.retrorental.backend.model.Administrador;
import com.retrorental.backend.model.Empleado;
import com.retrorental.backend.model.Persona;
import com.retrorental.backend.exception.ConflictException;
import com.retrorental.backend.exception.ErrorCode;
import com.retrorental.backend.exception.InvalidCredentialsException;
import com.retrorental.backend.model.embeddable.Direccion;
import com.retrorental.backend.model.embeddable.Telefono;
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
            throw new ConflictException(
                ErrorCode.EMAIL_ALREADY_EXISTS, "Ya existe un usuario con ese email", "email");
        }

        if (personaRepository.existsByDocumento(request.getDocumento())) {
            throw new ConflictException(
                ErrorCode.DOCUMENTO_ALREADY_EXISTS, "Ya existe un usuario con ese documento", "documento");
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

        Direccion direccion = new Direccion();
        direccion.setCalle(request.getDireccion().getCalle());
        direccion.setNumero(request.getDireccion().getNumero());
        direccion.setCiudad(request.getDireccion().getCiudad());
        direccion.setProvincia(request.getDireccion().getProvincia());
        direccion.setCodigoPostal(request.getDireccion().getCodigoPostal());
        direccion.setBarrio(request.getDireccion().getBarrio());
        persona.setDireccion(direccion);

        Telefono telefono = new Telefono();
        telefono.setCodigoArea(request.getTelefono().getCodigoArea());
        telefono.setTelefono(request.getTelefono().getNumero());
        persona.setTelefono(telefono);

        personaRepository.save(persona);

        String token = jwtUtil.generateToken(persona.getEmail(), persona.getRol().name());

        return toAuthResponse(persona, token);
    }

    public AuthResponse login(LoginRequest request) {

        Persona persona = personaRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new InvalidCredentialsException("Email o contraseña incorrectos"));

        if (!passwordEncoder.matches(request.getPassword(), persona.getPassword())) {
            throw new InvalidCredentialsException("Email o contraseña incorrectos");
        }

        String token = jwtUtil.generateToken(persona.getEmail(), persona.getRol().name());

        return toAuthResponse(persona, token);
    }

    // Arma la respuesta de auth desde la persona ya persistida. Formatea el
    // teléfono ("codigoArea numero") y tolera que no haya uno cargado (null).
    private AuthResponse toAuthResponse(Persona persona, String token) {
        Telefono tel = persona.getTelefono();
        String telefono = tel != null ? (tel.getCodigoArea() + " " + tel.getTelefono()).trim() : null;
        return new AuthResponse(
            token,
            persona.getNombre(),
            persona.getApellido(),
            persona.getEmail(),
            persona.getRol(),
            telefono
        );
    }
}