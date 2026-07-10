package com.retrorental.backend.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(MinioProperties.class)
@RequiredArgsConstructor
public class MinioConfig {

    private final MinioProperties properties;

    // Cliente interno: opera contra MinIO (subir, borrar, crear bucket).
    @Bean
    @Primary
    public MinioClient minioClient() {
        return MinioClient.builder()
            .endpoint(properties.getEndpoint())
            .region(properties.getRegion())
            .credentials(properties.getAccessKey(), properties.getSecretKey())
            .build();
    }

    // Cliente publico: solo se usa para FIRMAR URLs presignadas con el host que
    // el cliente final puede alcanzar. Firmar no abre conexiones al servidor.
    @Bean("minioPublicClient")
    public MinioClient minioPublicClient() {
        return MinioClient.builder()
            .endpoint(properties.resolvePublicEndpoint())
            .region(properties.getRegion())
            .credentials(properties.getAccessKey(), properties.getSecretKey())
            .build();
    }
}
