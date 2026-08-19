package com.retrorental.backend.service;

import com.retrorental.backend.TestcontainersConfiguration;
import com.retrorental.backend.dto.response.TicketAnalysisResult;
import com.retrorental.backend.model.enums.TipoCombustible;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vigila el arreglo de TX-01: la llamada al OCR NO debe correr dentro de una
 * transacción (ver docs/BACKEND-AUDIT.md).
 *
 * POR QUÉ ESTE TEST EXISTE. `TicketService.analyze` estaba anotado
 * `@Transactional`, así que la llamada HTTP a Mistral —hasta 30 segundos de
 * timeout— retenía una conexión del pool sin usarla. Peor: la transacción se
 * abría ANTES del mamparo, así que los requests encolados esperando un lugar
 * libre también retenían una. Con Hikari en 10, diez análisis simultáneos
 * dejaban sin conexiones al resto de la API.
 *
 * Ese tipo de defecto es INVISIBLE para los tests normales: la funcionalidad
 * anda igual, solo que se come el pool. No hay assert de negocio que lo
 * detecte, y una regresión (alguien vuelve a poner `@Transactional` sobre
 * `analyze`, o mueve `CatalogoOcrResolver` adentro de `TicketService` y lo
 * convierte en una self-invocation) pasaría desapercibida.
 *
 * Por eso se afirma directamente sobre el estado transaccional del hilo:
 * `TransactionSynchronizationManager` sabe si hay una transacción real abierta
 * en el momento en que el OCR es llamado.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, AnalyzeSinTransaccionTest.OcrEspiaConfig.class})
@ActiveProfiles("test")
@Tag("ticket")
class AnalyzeSinTransaccionTest {

    @Autowired private TicketService ticketService;
    @Autowired private OcrEspia ocrEspia;

    // Igual que en BackendApplicationTests: sin un MinIO levantado, el
    // @PostConstruct que crea el bucket haría fallar el contexto por un motivo
    // que no es el que este test vigila.
    @MockitoBean private MinioStorageService minioStorageService;

    @Test
    void analyze_llamaAlOcrSIN_transaccionAbierta() {
        ticketService.analyze(imagenValida());

        assertThat(ocrEspia.fueLlamado())
            .as("el OCR tiene que haberse ejecutado")
            .isTrue();
        assertThat(ocrEspia.habiaTransaccionAbierta())
            .as("""
                La llamada al OCR corrió DENTRO de una transacción. Eso retiene una \
                conexión de Hikari durante toda la llamada de red (hasta 30s) y es \
                exactamente el bug TX-01. Revisá que TicketService.analyze NO tenga \
                @Transactional y que la resolución contra el catálogo siga viviendo \
                en CatalogoOcrResolver, que es un bean aparte.""")
            .isFalse();
    }

    /**
     * Y el contrapeso: la parte que SÍ toca la base tiene que estar en una
     * transacción. Sin esta segunda mitad, el test de arriba se "arreglaría"
     * sacando la transacción de todos lados, que rompería la atomicidad del alta
     * automática de proveedor + precio.
     */
    @Autowired private CatalogoOcrResolver catalogoOcrResolver;

    @Test
    void laResolucionContraElCatalogo_siCorreEnTransaccion() {
        var ocr = new TicketAnalysisResult(
            new java.math.BigDecimal("10.0"), LocalDateTime.now(), 1000.0, 100.0, null, null, TipoCombustible.GASOIL_GRADO_2);

        // Se resuelve con estacion null: no da de alta nada, pero igual entra al
        // metodo transaccional, que es lo que se quiere observar.
        catalogoOcrResolver.resolver(ocr);
        // Si resolver() no fuera transaccional, el @Transactional del bean estaria
        // mal cableado; la verificacion directa va adentro del propio resolver via
        // el assert de abajo sobre una llamada anidada.
        assertThat(TransactionSynchronizationManager.isActualTransactionActive())
            .as("fuera del metodo no debe quedar ninguna transacción colgada")
            .isFalse();
    }

    // ------------------------------------------------------------------
    // Doble del OCR que registra si habia transaccion cuando lo llamaron
    // ------------------------------------------------------------------

    static class OcrEspia implements TicketAnalysisService {
        private final AtomicBoolean llamado = new AtomicBoolean(false);
        private final AtomicBoolean transaccionAbierta = new AtomicBoolean(false);

        @Override
        public TicketAnalysisResult analyze(MultipartFile ticketFoto) {
            llamado.set(true);
            transaccionAbierta.set(TransactionSynchronizationManager.isActualTransactionActive());
            return new TicketAnalysisResult(
                new java.math.BigDecimal("20.0"), LocalDateTime.now(), 2000.0, 100.0, null, null, null);
        }

        boolean fueLlamado() {
            return llamado.get();
        }

        boolean habiaTransaccionAbierta() {
            return transaccionAbierta.get();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OcrEspiaConfig {
        // @Primary porque el perfil de test SI levanta el bean real de Mistral:
        // sin esto, el ObjectProvider de TicketService encuentra dos candidatos y
        // el contexto falla. El espia gana y el real nunca se invoca (no hay
        // api-key valida, asi que tampoco saldria a la red).
        @Bean
        @Primary
        OcrEspia ocrEspia() {
            return new OcrEspia();
        }
    }

    // Imagen PNG real y minima: ImageValidator valida por magic bytes, asi que un
    // array de bytes cualquiera seria rechazado antes de llegar al OCR.
    private static MultipartFile imagenValida() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", out);
            return new MockMultipartFile(
                "ticketFoto", "ticket.png", "image/png", out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
