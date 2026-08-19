package com.retrorental.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registro de eventos de SEGURIDAD (ver docs/SECURITY-AUDIT.md, SEC-12).
 *
 * Antes no quedaba traza de logins fallidos, 403 ni cortes por rate limit: ante
 * un incidente no habia forma de reconstruir que paso, desde donde ni contra que
 * cuentas. Esa reconstruccion es justamente lo unico que se necesita cuando algo
 * ya ocurrio.
 *
 * <h2>Un logger con nombre propio</h2>
 * Todos los eventos salen bajo el nombre {@code SECURITY}, no bajo el de la
 * clase que los emite. Eso permite tres cosas que con loggers por clase no se
 * pueden: filtrar el flujo de seguridad con un solo grep, subirle o bajarle el
 * nivel sin tocar el resto de la app, y mandarlo a un appender aparte el dia que
 * haya agregacion de logs.
 *
 * <h2>Nivel WARN a proposito</h2>
 * En el perfil prod el nivel raiz es WARN. Si estos eventos fueran INFO se
 * perderian justo en el entorno donde importan.
 *
 * <h2>Que NO se loguea</h2>
 * Nunca la contrasena intentada, ni el token, ni el hash. Un log de seguridad
 * que filtra credenciales es una vulnerabilidad nueva, no un control: los logs
 * suelen tener lectores mas amplios que la base de datos.
 */
@Component
public class SecurityEventLogger {

    private static final Logger log = LoggerFactory.getLogger("SECURITY");

    // Spring inyecta un proxy que resuelve el request de CADA llamada, asi que
    // este bean puede ser singleton sin arrastrar el request de otro hilo.
    private final HttpServletRequest request;

    public SecurityEventLogger(HttpServletRequest request) {
        this.request = request;
    }

    /**
     * Intento de login rechazado. Se loguea el username TAL COMO LLEGO, sin
     * confirmar si existe: el evento sirve para detectar una fuerza bruta
     * (muchos intentos desde una IP, o contra un mismo usuario), y para eso el
     * valor crudo alcanza.
     */
    public void loginFallido(String username) {
        log.warn("Login rechazado - usuario='{}' ip={}", username, ipDeOrigen());
    }

    /** Peticion autenticada contra un recurso para el que no tiene permiso. */
    public void accesoDenegado(String metodo, String ruta) {
        log.warn("Acceso denegado (403) - {} {} ip={}", metodo, ruta, ipDeOrigen());
    }

    /** Corte por rate limit: la peticion no llego a ejecutarse. */
    public void rateLimitSuperado(String ruta, String ip) {
        log.warn("Rate limit superado - ruta={} ip={}", ruta, ip);
    }

    /**
     * IP real del cliente.
     *
     * Detras del nginx de infra/, getRemoteAddr() devolveria SIEMPRE la IP del
     * proxy y el log no serviria para nada. La app corre con
     * server.forward-headers-strategy=framework, asi que Spring ya resuelve
     * X-Forwarded-For antes de que el request llegue hasta aca y getRemoteAddr()
     * devuelve la IP de origen. Mismo criterio que LoginRateLimitFilter.
     */
    private String ipDeOrigen() {
        try {
            return request.getRemoteAddr();
        } catch (IllegalStateException e) {
            // Fuera de un request (por ejemplo, una tarea de fondo). No vale
            // tirar abajo la operacion por no poder anotar la IP.
            return "desconocida";
        }
    }
}
