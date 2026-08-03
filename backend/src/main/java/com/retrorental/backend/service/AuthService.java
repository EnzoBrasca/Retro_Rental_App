package com.retrorental.backend.service;

import com.retrorental.backend.dto.request.LoginRequest;
import com.retrorental.backend.dto.request.RegisterRequest;
import com.retrorental.backend.dto.response.AuthResponse;
import com.retrorental.backend.model.Empleado;
import com.retrorental.backend.model.Persona;
import com.retrorental.backend.exception.ConflictException;
import com.retrorental.backend.exception.ErrorCode;
import com.retrorental.backend.exception.InvalidCredentialsException;
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
    private final UsernameGenerator usernameGenerator;

    public AuthResponse register(RegisterRequest request) {

        if (personaRepository.existsByDocumento(request.getDocumento())) {
            throw new ConflictException(
                ErrorCode.DOCUMENTO_ALREADY_EXISTS, "Ya existe un usuario con ese documento", "documento");
        }

        // El registro público SIEMPRE crea un empleado. El rol no se toma del
        // request: dejar que el cliente lo eligiera significaba que cualquiera con
        // la app se daba permisos de administrador. Las altas de administrador se
        // hacen fuera de este endpoint (ver docs/DEPLOYMENT.md).
        Empleado persona = new Empleado();
        persona.setFechaAlta(LocalDate.now());

        persona.setNombre(request.getNombre());
        persona.setApellido(request.getApellido());
        persona.setDocumento(request.getDocumento());
        persona.setPassword(passwordEncoder.encode(request.getPassword()));
        persona.setRol(Rol.EMPLEADO);

        String username = usernameGenerator.generate(request.getNombre(), request.getApellido());
        persona.setUsername(username);

        Telefono telefono = new Telefono();
        telefono.setCodigoArea(request.getTelefono().getCodigoArea());
        telefono.setTelefono(request.getTelefono().getNumero());
        persona.setTelefono(telefono);

        personaRepository.save(persona);

        String token = jwtUtil.generateToken(persona.getUsername(), persona.getRol().name());

        return toAuthResponse(persona, token);
    }

    public AuthResponse login(LoginRequest request) {

        Persona persona = personaRepository.findByUsername(request.getUsername())
            .orElseThrow(() -> new InvalidCredentialsException("Usuario o contraseña incorrectos"));

        if (!passwordEncoder.matches(request.getPassword(), persona.getPassword())) {
            throw new InvalidCredentialsException("Usuario o contraseña incorrectos");
        }

        // Un empleado dado de baja no puede loguearse. Mismo mensaje genérico
        // que el resto de los rechazos de este método: no revela si la cuenta
        // existe o si simplemente está inactiva.
        if (persona instanceof Empleado emp && emp.getFechaBaja() != null) {
            throw new InvalidCredentialsException("Usuario o contraseña incorrectos");
        }

        String token = jwtUtil.generateToken(persona.getUsername(), persona.getRol().name());

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
            persona.getUsername(),
            persona.getRol(),
            telefono,
            jwtUtil.extractExpirationMillis(token)
        );
    }
}
