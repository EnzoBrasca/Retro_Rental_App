package com.retrorental.backend.security;

import com.retrorental.backend.model.Empleado;
import com.retrorental.backend.model.Persona;
import com.retrorental.backend.repository.PersonaRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Autentica cada request a partir del JWT del header Authorization.
 *
 * NO alcanza con verificar la firma. Un token valido dice quien ERA el usuario
 * y que rol tenia en el momento de emitirlo; no dice nada de su estado AHORA.
 * Antes, este filtro construia la autenticacion con el subject y el claim "rol"
 * sin consultar la base, con dos consecuencias:
 *
 *  - Un empleado dado de baja seguia operando con normalidad hasta que su token
 *    venciera. No habia forma de expulsar a nadie en el momento.
 *  - Un cambio de rol no surtia efecto hasta la siguiente emision.
 *
 * Por eso ahora la persona se carga de la base en cada request: si no existe o
 * esta dada de baja, el token se ignora, y el ROL sale de la fila y no del
 * claim. El costo es una consulta por request autenticado. A la escala de esta
 * app (una empresa, un punado de empleados) es irrelevante, y se prefiere
 * pagarlo antes que sostener un modelo donde revocar el acceso es imposible.
 * Si algun dia el volumen lo justifica, el lugar para un cache corto es aca.
 */
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final PersonaRepository personaRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // Si no hay header o no empieza con "Bearer ", deja pasar sin autenticar
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7); // Saca el "Bearer " del inicio

        if (!jwtUtil.isTokenValid(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        String username = jwtUtil.extractUsername(token);

        // La firma es valida, pero eso solo prueba que el token lo emitimos
        // nosotros. Quien lo presenta puede haber sido dado de baja despues.
        Optional<Persona> persona = personaRepository.findByUsername(username)
            .filter(JwtFilter::estaActiva);

        if (persona.isEmpty()) {
            // Sin autenticar: la cadena de seguridad responde 401. No se
            // distingue "no existe" de "dado de baja" — para el portador del
            // token el resultado es el mismo, y el motivo no es asunto suyo.
            filterChain.doFilter(request, response);
            return;
        }

        // El rol sale de la BASE, no del claim del token: asi un cambio de rol
        // aplica en el proximo request y no recien cuando el token venza.
        String rol = persona.get().getRol().name();

        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(
                username,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + rol))
            );

        SecurityContextHolder.getContext().setAuthentication(auth);
        filterChain.doFilter(request, response);
    }

    /**
     * Un empleado con fecha de baja perdio el acceso. Un administrador no tiene
     * baja logica, asi que siempre cuenta como activo.
     *
     * Mismo criterio que AuthService.login, a proposito: si el login rechaza a
     * alguien, su token viejo tampoco puede seguir sirviendo. Que las dos
     * puertas usen la misma regla es lo que evita que una quede abierta.
     */
    private static boolean estaActiva(Persona persona) {
        return !(persona instanceof Empleado empleado) || empleado.getFechaBaja() == null;
    }
}