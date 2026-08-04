package com.retrorental.backend;

import com.retrorental.backend.service.MinioStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Levanta el contexto COMPLETO contra un Postgres real (Testcontainers).
 *
 * No es un test de humo decorativo: es el unico que ejerce la capa de datos de
 * punta a punta. Flyway aplica las migraciones sobre una base vacia y despues
 * Hibernate valida las entidades contra el schema resultante, porque ddl-auto
 * es `validate` en todos los perfiles. Si alguien agrega un campo a una entidad
 * y se olvida la migracion, el contexto no levanta y este test se pone en rojo.
 * Sin el, eso se descubre cuando la app no arranca en produccion.
 *
 * El resto de la suite son @WebMvcTest: no cargan JPA ni Flyway, asi que no ven
 * nada de esto.
 *
 * MinioStorageService va mockeado a proposito: tiene un @PostConstruct que crea
 * el bucket, y sin un MinIO levantado el contexto fallaria por una razon que no
 * es la que este test quiere vigilar.
 *
 * Requiere Docker corriendo.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class BackendApplicationTests {

    @MockitoBean
    private MinioStorageService minioStorageService;

    @Test
    void contextLoads() {
    }
}
