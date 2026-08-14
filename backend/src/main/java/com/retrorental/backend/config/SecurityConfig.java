package com.retrorental.backend.config;

import com.retrorental.backend.dto.response.ApiError;
import com.retrorental.backend.exception.ErrorCode;
import com.retrorental.backend.security.AnalyzeRateLimitFilter;
import com.retrorental.backend.security.JwtFilter;
import com.retrorental.backend.security.LoginRateLimitFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final LoginRateLimitFilter loginRateLimitFilter;
    private final AnalyzeRateLimitFilter analyzeRateLimitFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                // Endpoints públicos — no requieren token. /error debe ser
                // público: cuando un controller lanza una excepción, Spring hace
                // un forward interno a /error; si no estuviera permitido, la
                // seguridad lo bloquearía con 403 y enmascararía el error real.
                //
                // Se listan uno por uno y NO como /auth/**: con el comodín,
                // cualquier endpoint que se agregue bajo /auth nace público sin
                // que nadie lo decida. Que /auth/register sea público es una
                // decisión explícita (los empleados se dan de alta solos desde
                // la app), no un efecto secundario de un comodín.
                //
                // Ambos endpoints estan sujetos a LoginRateLimitFilter: son la
                // unica superficie sin autenticar de la API.
                .requestMatchers("/auth/login", "/auth/register", "/health", "/error").permitAll()
                // Endpoints solo para administradores
                .requestMatchers("/admin/**").hasRole("ADMINISTRADOR")
                // Cualquier otro endpoint requiere estar autenticado
                .anyRequest().authenticated()
            )
            // Errores de seguridad como JSON ApiError (mismo contrato que el
            // resto de la API), no como respuestas vacías del filtro.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint())
                .accessDeniedHandler(accessDeniedHandler())
            )
            // El limitador va ANTES que el JwtFilter: un intento de login que ya
            // supero el limite se corta sin gastar nada del resto de la cadena.
            .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            // Este va DESPUES del JwtFilter, no antes: limita /tickets/analyze
            // por usuario autenticado, y el usuario lo deja en el
            // SecurityContext el JwtFilter. Invertir el orden lo dejaria sin
            // nadie a quien contarle la llamada, y el limite no aplicaria nunca.
            .addFilterAfter(analyzeRateLimitFilter, JwtFilter.class);

        return http.build();
    }

    // 401: sin token o token inválido/expirado.
    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> writeError(
            response, ErrorCode.UNAUTHENTICATED,
            "Necesitas iniciar sesión para acceder a este recurso");
    }

    // 403: autenticado pero sin el rol requerido (ej. empleado en /admin/**).
    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> writeError(
            response, ErrorCode.ACCESS_DENIED,
            "No tenes permiso para acceder a este recurso");
    }

    private void writeError(HttpServletResponse response, ErrorCode code, String message)
            throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiError body = new ApiError(code.getStatus().value(), code.name(), message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}