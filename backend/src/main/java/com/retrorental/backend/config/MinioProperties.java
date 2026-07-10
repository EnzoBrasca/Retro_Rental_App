package com.retrorental.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    // Endpoint interno: lo usa el backend para hablar con MinIO (put/get/remove).
    private String endpoint;

    // Endpoint publico: host con el que se firman las URLs presignadas para que
    // un cliente externo (app mobile) pueda descargarlas. Si es null, usa endpoint.
    private String publicEndpoint;

    private String accessKey;

    private String secretKey;

    private String bucket;

    // Región de MinIO. Se fija explícitamente para que el SDK NO haga un
    // GetBucketLocation al firmar URLs presignadas: con el endpoint público
    // (localhost) esa llamada no es alcanzable desde el contenedor y rompía la
    // firma con "Connection refused". MinIO usa "us-east-1" por defecto.
    private String region = "us-east-1";

    // Validez de las URLs presignadas, en segundos (por defecto 1 hora).
    private int presignedExpirySeconds = 3600;

    public String resolvePublicEndpoint() {
        return (publicEndpoint == null || publicEndpoint.isBlank()) ? endpoint : publicEndpoint;
    }
}
