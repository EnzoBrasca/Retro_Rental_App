package com.retrorental.backend.config;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(MistralProperties.class)
@RequiredArgsConstructor
public class MistralConfig {

    private final MistralProperties properties;

    // El cliente SOLO se crea si hay api-key configurada. Asi la app puede
    // arrancar sin la key (durante el setup) y el servicio de analisis, que
    // depende de este bean, tampoco se instancia hasta que la key exista.
    @Bean("mistralRestClient")
    @ConditionalOnProperty(prefix = "mistral", name = "api-key")
    public RestClient mistralRestClient() {
        Duration timeout = Duration.ofSeconds(properties.getTimeoutSeconds());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);

        return RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }
}
