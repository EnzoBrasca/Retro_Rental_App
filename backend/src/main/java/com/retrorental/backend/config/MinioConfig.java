package com.retrorental.backend.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

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
            // Timeouts EXPLICITOS: sin esto, un MinIO que no responde bloquea la
            // subida indefinidamente y con ella el hilo que esta dando de alta un
            // ticket. Ver MinioProperties.timeoutSeconds.
            .httpClient(httpClientConTimeouts())
            .build();
    }

    // Cliente publico: solo se usa para FIRMAR URLs presignadas con el host que
    // el cliente final puede alcanzar. Firmar no abre conexiones al servidor.
    //
    // Igual lleva timeouts: la firma es local, pero el SDK puede hacer llamadas
    // de red en otros caminos (por ejemplo GetBucketLocation si la region no
    // estuviera fijada), y un default sin timeout es una espera infinita
    // esperando a pasar.
    @Bean("minioPublicClient")
    public MinioClient minioPublicClient() {
        return MinioClient.builder()
            .endpoint(properties.resolvePublicEndpoint())
            .region(properties.getRegion())
            .credentials(properties.getAccessKey(), properties.getSecretKey())
            .httpClient(httpClientConTimeouts())
            .build();
    }

    private OkHttpClient httpClientConTimeouts() {
        Duration timeout = Duration.ofSeconds(properties.getTimeoutSeconds());
        return new OkHttpClient.Builder()
            .connectTimeout(timeout)
            .readTimeout(timeout)
            .writeTimeout(timeout)
            .build();
    }
}
