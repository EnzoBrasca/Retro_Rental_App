package com.retrorental.backend.service;

import com.retrorental.backend.dto.request.LoginRequest;
import com.retrorental.backend.dto.request.RegisterRequest;
import com.retrorental.backend.dto.response.AuthResponse;
import com.retrorental.backend.model.Empleado;
import com.retrorental.backend.model.EmpleadoHabilitado;
import com.retrorental.backend.model.Persona;
import com.retrorental.backend.exception.ErrorCode;
import com.retrorental.backend.exception.ForbiddenException;
import com.retrorental.backend.exception.InvalidCredentialsException;
import com.retrorental.backend.model.embeddable.Telefono;
import com.retrorental.backend.model.enums.Rol;
import com.retrorental.backend.repository.PersonaRepository;
import com.retrorental.backend.security.JwtUtil;
import com.retrorental.backend.security.SecurityEventLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final PersonaRepository personaRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UsernameGenerator usernameGenerator;
    private final HabilitadoService habilitadoService;
    private final SecurityEventLogger securityEventLogger;

    /**
     * Registro publico de empleados.
     *
     * Sigue siendo self-service y el formulario no cambio: lo unico que se
     * agrega es que el documento tiene que estar en el padron que carga el
     * administrador (ver HabilitadoService). Sin ese filtro, "publico"
     * significaba que cualquier persona de internet con la URL de la API
     * obtenia una cuenta EMPLEADO valida.
     *
     * Orden de los chequeos, a proposito:
     *
     *  1. Padron. Va PRIMERO para que el rechazo por documento desconocido sea
     *     indistinguible del rechazo por documento no habilitado. Si el chequeo
     *     de duplicado fuera primero, su 409 delataria que ese documento tiene
     *     cuenta, para CUALQUIER numero probado.
     *  2. Documento duplicado. Documento que ya existe se rechaza SIEMPRE.
     *     Esta regla no admite excepciones ni condiciones — es lo que hace
     *     imposible que un registro se apropie de una cuenta ajena. Lo unico
     *     que cambio es la RESPUESTA: antes devolvia un 409
     *     DOCUMENTO_ALREADY_EXISTS, que confirmaba a cualquiera que ese
     *     documento tenia cuenta. Ahora devuelve el mismo rechazo generico que
     *     el padron, asi el registro publico tiene una sola respuesta de
     *     fracaso y no sirve para averiguar nada.
     *
     *     El 409 explicito sigue existiendo, pero solo en POST /admin/empleados
     *     (ver EmpleadoService): ahi quien pregunta es un administrador
     *     autenticado que necesita saber por que fallo el alta.
     *
     * Transaccional: la persona y el consumo de su habilitacion se guardan o
     * se descartan juntos. Sin esto, un fallo despues del insert dejaria la
     * cuenta creada con la habilitacion todavia libre, reutilizable por otro.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {

        EmpleadoHabilitado habilitado =
            habilitadoService.validarHabilitacion(request.getDocumento(), request.getApellido());

        if (personaRepository.existsByDocumento(request.getDocumento())) {
            throw new ForbiddenException(
                ErrorCode.REGISTRO_NO_HABILITADO, HabilitadoService.RECHAZO_REGISTRO);
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

        // La habilitacion se consume recien aca, con la cuenta ya creada: si el
        // alta hubiera fallado, el empleado real todavia puede reintentar.
        habilitadoService.marcarUsado(habilitado, persona);

        String token = jwtUtil.generateToken(persona.getUsername(), persona.getRol().name());

        return toAuthResponse(persona, token);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {

        // Los tres rechazos de abajo devuelven el MISMO mensaje al cliente (no
        // revelar si la cuenta existe), pero los tres se LOGUEAN: hacia afuera
        // son indistinguibles, hacia adentro quedan registrados (ver
        // docs/SECURITY-AUDIT.md, SEC-12).
        Persona persona = personaRepository.findByUsername(request.getUsername())
            .orElseThrow(() -> {
                securityEventLogger.loginFallido(request.getUsername());
                return new InvalidCredentialsException("Usuario o contraseña incorrectos");
            });

        if (!passwordEncoder.matches(request.getPassword(), persona.getPassword())) {
            securityEventLogger.loginFallido(request.getUsername());
            throw new InvalidCredentialsException("Usuario o contraseña incorrectos");
        }

        // Un empleado dado de baja no puede loguearse. Mismo mensaje genérico
        // que el resto de los rechazos de este método: no revela si la cuenta
        // existe o si simplemente está inactiva.
        if (persona instanceof Empleado emp && emp.getFechaBaja() != null) {
            securityEventLogger.loginFallido(request.getUsername());
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
