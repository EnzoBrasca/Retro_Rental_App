package com.retrorental.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Postgres efimero para los tests que necesitan una base de verdad.
 *
 * @ServiceConnection le pasa solo a Spring la url, el usuario y la password del
 * contenedor, sin tener que declarar nada a mano. Eso ademas resuelve el
 * problema de fondo que tenia BackendApplicationTests: las credenciales reales
 * viven en un .env que se carga en main() con dotenv, y los tests NUNCA pasan
 * por main() (@SpringBootTest arranca el contexto directamente). Por eso el
 * datasource intentaba conectarse con el usuario literal "${DB_USER}".
 *
 * La imagen es la misma que corre en produccion (postgres:16): un test contra
 * otro motor, u otra version, no probaria lo que importa.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:16");
    }
}
