package com.retrorental.backend.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Contador de ventana fija en memoria, por clave.
 *
 * Se cuentan los intentos de cada clave y, al superar el maximo dentro de la
 * ventana, {@link #excedeLimite} devuelve true hasta que la ventana vence.
 *
 * Existe como clase aparte porque hay DOS frenos distintos en la API
 * (fuerza bruta de login por IP, y consumo de OCR por usuario) y la logica de
 * ventana es identica en los dos. Duplicarla significaba mantener dos copias de
 * un control de seguridad: el dia que se arregla un borde en una, la otra queda
 * atras sin que nadie lo note.
 *
 * El estado NO se comparte entre instancias ni sobrevive a un reinicio; hoy
 * corre una sola instancia del backend, asi que alcanza. Si algun dia se escala
 * horizontalmente, esto tiene que pasar a un contador compartido (Redis).
 */
public class FixedWindowCounter {

    /**
     * A partir de cuantas claves distintas se hace limpieza de ventanas
     * vencidas. Sin esto el mapa crece sin techo si alguien rota direcciones de
     * origen o cuentas.
     */
    private static final int CLEANUP_THRESHOLD = 1_000;

    private final int maxAttempts;
    private final Duration window;
    private final Map<String, Window> ventanasPorClave = new ConcurrentHashMap<>();

    public FixedWindowCounter(int maxAttempts, Duration window) {
        this.maxAttempts = maxAttempts;
        this.window = window;
    }

    /** Registra un intento de esa clave y devuelve si ya paso del maximo. */
    public boolean excedeLimite(String clave) {
        Instant ahora = Instant.now();

        if (ventanasPorClave.size() > CLEANUP_THRESHOLD) {
            ventanasPorClave.values().removeIf(w -> w.vencio(ahora, window));
        }

        Window ventana = ventanasPorClave.compute(clave, (k, actual) ->
            (actual == null || actual.vencio(ahora, window)) ? new Window(ahora) : actual);

        return ventana.intentos.incrementAndGet() > maxAttempts;
    }

    /** Duracion de la ventana, para el header Retry-After. */
    public long segundosDeVentana() {
        return window.toSeconds();
    }

    /** Contador de intentos de una clave dentro de una ventana que arranca en {@code inicio}. */
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
