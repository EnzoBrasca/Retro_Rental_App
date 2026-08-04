package com.retrorental.backend.security;

import com.retrorental.backend.dto.response.ApiError;
import com.retrorental.backend.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limita por IP de origen los POST publicos de la API (/auth/login y
 * /auth/register).
 *
 * Sin esto, /auth/login queda expuesto a fuerza bruta y /auth/register a la
 * creacion masiva de cuentas: la API es publica y no hay nada que frene a
 * alguien golpeandola a la velocidad de la red.
 *
 * Ventana fija en memoria: se cuentan los intentos de cada IP y, al superar el
 * maximo dentro de la ventana, se responde 429 hasta que la ventana vence. El
 * estado NO se comparte entre instancias ni sobrevive a un reinicio; hoy corre
 * una sola instancia del backend, asi que alcanza. Si algun dia se escala
 * horizontalmente, esto tiene que pasar a un contador compartido (Redis).
 *
 * Se cuentan TODOS los intentos, no solo los fallidos. Es a proposito: contar
 * solo fallos obliga a dejar pasar el request para saber como termino, y un
 * atacante que acierta una contrasena no deberia poder seguir probando gratis.
 *
 * La IP sale de request.getRemoteAddr(). Detras de nginx eso seria siempre la
 * IP del contenedor del proxy, que dejaria a todo el mundo compartiendo un solo
 * contador: el primer atacante bloquearia a todos los empleados. Por eso
 * application.yml activa server.forward-headers-strategy=framework, que hace
 * que Spring lea X-Forwarded-For (nginx lo envia). Es seguro confiar en ese
 * header porque el backend no publica puertos en produccion: el unico camino
 * hacia el es nginx.
 */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    /**
     * Rutas protegidas: los dos POST publicos de la API.
     *
     * /auth/login por fuerza bruta de contrasenas. /auth/register porque es
     * publico a proposito (los empleados se dan de alta solos desde la app) y
     * sin freno cualquiera puede crear cuentas en masa contra la base.
     */
    private static final Set<String> RUTAS_LIMITADAS = Set.of("/auth/login", "/auth/register");

    /**
     * A partir de cuantas IPs distintas se hace limpieza de ventanas vencidas.
     * Sin esto el mapa crece sin techo si alguien rota direcciones de origen.
     */
    private static final int CLEANUP_THRESHOLD = 1_000;

    private final ObjectMapper objectMapper;
    private final int maxAttempts;
    private final Duration window;

    private final Map<String, Window> windowsPorIp = new ConcurrentHashMap<>();

    public LoginRateLimitFilter(
            ObjectMapper objectMapper,
            @Value("${app.rate-limit.login.max-attempts:10}") int maxAttempts,
            @Value("${app.rate-limit.login.window-seconds:60}") long windowSeconds) {
        this.objectMapper = objectMapper;
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    /**
     * Solo se limitan los POST publicos; el resto de la API no pasa por aca.
     *
     * La ruta se saca de getRequestURI() y no de getServletPath(): este ultimo
     * depende de como el contenedor haya mapeado el servlet y puede venir vacio
     * (pasa en MockMvc), con lo cual el filtro no se aplicaria nunca.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(HttpMethod.POST.matches(request.getMethod())
            && RUTAS_LIMITADAS.contains(rutaSinContexto(request)));
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

        if (excedeLimite(request.getRemoteAddr())) {
            long segundos = window.toSeconds();
            response.setHeader("Retry-After", String.valueOf(segundos));
            escribirError(response, "Demasiados intentos. Espera "
                + segundos + " segundos y volve a intentar.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** Registra un intento de esa IP y devuelve si ya paso del maximo. */
    private boolean excedeLimite(String ip) {
        Instant ahora = Instant.now();

        if (windowsPorIp.size() > CLEANUP_THRESHOLD) {
            windowsPorIp.values().removeIf(w -> w.vencio(ahora, window));
        }

        Window ventana = windowsPorIp.compute(ip, (clave, actual) ->
            (actual == null || actual.vencio(ahora, window)) ? new Window(ahora) : actual);

        return ventana.intentos.incrementAndGet() > maxAttempts;
    }

    private void escribirError(HttpServletResponse response, String mensaje) throws IOException {
        ErrorCode code = ErrorCode.TOO_MANY_REQUESTS;
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiError body = new ApiError(code.getStatus().value(), code.name(), mensaje);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    /** Contador de intentos de una IP dentro de una ventana que arranca en {@code inicio}. */
    private static final class Window {
        private final Instant inicio;
        private final AtomicInteger intentos = new AtomicInteger();

        private Window(Instant inicio) {
            this.inicio = inicio;
        }

        private boolean vencio(Instant ahora, Duration duracion) {
            return inicio.plus(duracion).isBefore(ahora);
        }
    }
}
