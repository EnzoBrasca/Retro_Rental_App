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
}
