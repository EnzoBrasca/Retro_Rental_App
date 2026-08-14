package com.retrorental.backend.security;

import com.retrorental.backend.dto.response.ApiError;
import com.retrorental.backend.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;

/**
 * Limita POST /tickets/analyze por USUARIO autenticado.
 *
 * Ese endpoint es la unica operacion de la API que cuesta dinero por llamada:
 * cada request dispara un OCR pago de Mistral, con hasta 10MB de imagen y 30
 * segundos de timeout. Sin freno, un solo cliente en bucle hace dos danos a la
 * vez: vacia el credito de la cuenta de Mistral, y deja hilos de Tomcat
 * bloqueados 30 segundos cada uno hasta que la API no le responde a nadie mas.
 * No hace falta intencion maliciosa: un reintento mal implementado en la app
 * produce exactamente el mismo efecto.
 *
 * Por que por usuario y no por IP, como el limite de login: los empleados
 * cargan tickets desde el campo, con datos moviles. Varios comparten la IP de
 * salida de la operadora, asi que un limite por IP castigaria a companeros que
 * no hicieron nada. La cuenta, en cambio, identifica a quien realmente esta
 * consumiendo. Y como /tickets/analyze exige autenticacion, siempre hay cuenta
 * a la que atribuirle el consumo — a diferencia de /auth/login, donde todavia
 * no la hay y la IP es lo unico que existe.
 *
 * ORDEN EN LA CADENA: va DESPUES de JwtFilter (ver SecurityConfig), que es
 * quien deja la autenticacion en el SecurityContext. Antes de el, este filtro
 * no tendria a quien contarle la llamada.
 *
 * Este filtro corta el request antes de que Spring resuelva el multipart, asi
 * que un pedido rechazado no llega siquiera a parsear la imagen. Lo que NO
 * evita es que el cuerpo se haya subido: para frenar eso hace falta un limite
 * en nginx (ver SEC-07 en docs/SECURITY-AUDIT.md).
 */
@Component
public class AnalyzeRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AnalyzeRateLimitFilter.class);

    private static final String RUTA_LIMITADA = "/tickets/analyze";

    private final ObjectMapper objectMapper;
    private final FixedWindowCounter contador;

    public AnalyzeRateLimitFilter(
            ObjectMapper objectMapper,
            @Value("${app.rate-limit.analyze.max-attempts:20}") int maxAttempts,
            @Value("${app.rate-limit.analyze.window-seconds:3600}") long windowSeconds) {
        this.objectMapper = objectMapper;
        this.contador = new FixedWindowCounter(maxAttempts, Duration.ofSeconds(windowSeconds));
    }

    /**
     * Solo se limita el POST de analisis; el resto de la API no pasa por aca.
     *
     * La ruta se saca de getRequestURI() y no de getServletPath(): este ultimo
     * depende de como el contenedor haya mapeado el servlet y puede venir vacio
     * (pasa en MockMvc), con lo cual el filtro no se aplicaria nunca.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(HttpMethod.POST.matches(request.getMethod())
            && RUTA_LIMITADA.equals(rutaSinContexto(request)));
    }

    private static String rutaSinContexto(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contexto = request.getContextPath();
        return (contexto != null && !contexto.isEmpty() && uri.startsWith(contexto))
            ? uri.substring(contexto.length())
            : uri;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String usuario = usuarioAutenticado();

        // Sin autenticacion no hay a quien contarle la llamada. Se deja pasar a
        // proposito: la cadena de seguridad lo corta con 401 unas lineas mas
        // abajo, sin llegar al controller ni al OCR, que es lo que este filtro
        // protege. Contar por IP aca solo agregaria un limite que castiga a
        // quien comparte salida con un atacante, sin evitar ningun costo.
        if (usuario == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (contador.excedeLimite(usuario)) {
            // Se loguea porque una racha de estos es la senal de que algo esta
            // llamando en bucle: o un atacante, o un reintento roto en la app.
            log.warn("Limite de analisis OCR alcanzado para el usuario autenticado");

            long segundos = contador.segundosDeVentana();
            response.setHeader("Retry-After", String.valueOf(segundos));
            escribirError(response, "Alcanzaste el limite de analisis de tickets por hora. "
                + "Podes cargar el ticket a mano mientras tanto.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String usuarioAutenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        String nombre = auth.getName();
        return (nombre == null || nombre.isBlank()) ? null : nombre;
    }

    private void escribirError(HttpServletResponse response, String mensaje) throws IOException {
        ErrorCode code = ErrorCode.TOO_MANY_REQUESTS;
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiError body = new ApiError(code.getStatus().value(), code.name(), mensaje);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
