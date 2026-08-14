package com.retrorental.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "mistral")
public class MistralProperties {

    // API key de Mistral (console.mistral.ai). Sin este valor, el RestClient no se
    // crea (ver MistralConfig) y la app arranca igual: el analisis queda inactivo.
    private String apiKey;

    // Host base de la API. Se separa para poder apuntar a un mock en tests.
    private String baseUrl = "https://api.mistral.ai/v1/ocr";

    // Modelo de OCR. "mistral-ocr-latest" lee la imagen y, con un JSON schema,
    // devuelve los campos del ticket ya estructurados (document_annotation).
    private String model = "mistral-ocr-latest";

    // Timeout de conexion y lectura de cada request al modelo, en segundos.
    private int timeoutSeconds = 30;

    /**
     * Cuantos analisis de OCR pueden estar en vuelo AL MISMO TIEMPO.
     *
     * Es un mamparo (bulkhead), no un limite de uso: aunque cada usuario
     * respete su cuota horaria, veinte empleados subiendo una foto a la vez
     * dejarian veinte hilos de Tomcat bloqueados hasta 30 segundos, y cada uno
     * sosteniendo la imagen en base64 en memoria. Con el tope, el excedente
     * espera o se rechaza rapido, y el RESTO de la API sigue respondiendo.
     */
    private int maxConcurrent = 5;

    /**
     * Cuanto espera un analisis a que se libere un lugar antes de rendirse.
     *
     * Corto a proposito: si hay que esperar mucho, es preferible devolver un
     * 503 rapido y que el empleado cargue el ticket a mano, antes que dejarlo
     * mirando una pantalla trabada con un hilo del servidor retenido.
     */
    private int acquireTimeoutSeconds = 5;
}
