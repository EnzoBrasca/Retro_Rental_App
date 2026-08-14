package com.retrorental.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.retrorental.backend.exception.ErrorCode;
import com.retrorental.backend.exception.StorageException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * El validador es lo unico que separa "se suben fotos de tickets" de "se sube
 * cualquier cosa a un dominio nuestro que el navegador ejecuta". Por eso se
 * testea sobre todo el caso adverso: el archivo que MIENTE sobre lo que es.
 */
@Tag("ticket")
class ImageValidatorTest {

    private final ImageValidator validator = new ImageValidator();

    private MockMultipartFile archivo(byte[] contenido, String nombre, String contentType) {
        return new MockMultipartFile("ticketFoto", nombre, contentType, contenido);
    }

    private static byte[] concat(byte[] cabecera, String resto) {
        byte[] cola = resto.getBytes(StandardCharsets.UTF_8);
        byte[] r = new byte[cabecera.length + cola.length];
        System.arraycopy(cabecera, 0, r, 0, cabecera.length);
        System.arraycopy(cola, 0, r, cabecera.length, cola.length);
        return r;
    }

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final byte[] PNG = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] WEBP = {
        'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P'};

    // -----------------------------------------------------------------------
    // Formatos aceptados
    // -----------------------------------------------------------------------

    @Test
    void aceptaJpeg() {
        assertEquals(ImageValidator.Formato.JPEG,
            validator.validar(archivo(concat(JPEG, "datos"), "t.jpg", "image/jpeg")));
    }

    @Test
    void aceptaPng() {
        assertEquals(ImageValidator.Formato.PNG,
            validator.validar(archivo(concat(PNG, "datos"), "t.png", "image/png")));
    }

    @Test
    void aceptaWebp() {
        assertEquals(ImageValidator.Formato.WEBP,
            validator.validar(archivo(concat(WEBP, "datos"), "t.webp", "image/webp")));
    }

    // -----------------------------------------------------------------------
    // El caso que motiva todo esto
    // -----------------------------------------------------------------------

    /**
     * El ataque de SEC-04 tal cual: un HTML que se presenta como imagen. Antes
     * quedaba guardado como .html con content-type text/html y nginx lo servia
     * para que el navegador lo ejecutara, en el mismo dominio donde viven las
     * URLs presignadas de todas las fotos.
     */
    @Test
    void rechazaHtmlDisfrazadoDeJpeg() {
        byte[] html = "<script>alert(document.cookie)</script>".getBytes(StandardCharsets.UTF_8);

        StorageException ex = assertThrows(StorageException.class,
            () -> validator.validar(archivo(html, "foto.jpg", "image/jpeg")));

        assertEquals(ErrorCode.FILE_TYPE_NOT_ALLOWED, ex.getCode());
    }

    /**
     * El espejo del anterior: el header dice text/html y el nombre .html, pero
     * el contenido ES una imagen. Se acepta, porque lo que manda es el
     * contenido. Prueba que la decision NO mira el header en ningun sentido.
     */
    @Test
    void aceptaImagenRealAunqueElHeaderYElNombreMientan() {
        assertEquals(ImageValidator.Formato.JPEG,
            validator.validar(archivo(concat(JPEG, "datos"), "payload.html", "text/html")));
    }

    @Test
    void rechazaEjecutableDisfrazadoDeImagen() {
        // ELF: 7F 'E' 'L' 'F'
        byte[] elf = {0x7F, 'E', 'L', 'F', 0x02, 0x01, 0x01, 0x00};

        StorageException ex = assertThrows(StorageException.class,
            () -> validator.validar(archivo(elf, "t.png", "image/png")));

        assertEquals(ErrorCode.FILE_TYPE_NOT_ALLOWED, ex.getCode());
    }

    @Test
    void rechazaPdf() {
        byte[] pdf = "%PDF-1.7\n%mas".getBytes(StandardCharsets.UTF_8);

        assertEquals(ErrorCode.FILE_TYPE_NOT_ALLOWED,
            assertThrows(StorageException.class,
                () -> validator.validar(archivo(pdf, "t.jpg", "image/jpeg"))).getCode());
    }

    /** SVG es texto, y un SVG puede llevar <script> adentro: no entra. */
    @Test
    void rechazaSvg() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script/></svg>"
            .getBytes(StandardCharsets.UTF_8);

        assertEquals(ErrorCode.FILE_TYPE_NOT_ALLOWED,
            assertThrows(StorageException.class,
                () -> validator.validar(archivo(svg, "t.svg", "image/svg+xml"))).getCode());
    }

    // -----------------------------------------------------------------------
    // Bordes
    // -----------------------------------------------------------------------

    @Test
    void rechazaArchivoVacio() {
        StorageException ex = assertThrows(StorageException.class,
            () -> validator.validar(archivo(new byte[0], "t.jpg", "image/jpeg")));

        assertEquals(ErrorCode.FILE_EMPTY, ex.getCode());
    }

    @Test
    void rechazaNull() {
        assertEquals(ErrorCode.FILE_EMPTY,
            assertThrows(StorageException.class, () -> validator.validar(null)).getCode());
    }

    /** Un archivo mas corto que la firma no puede coincidir con ninguna. */
    @Test
    void rechazaArchivoMasCortoQueLaFirma() {
        byte[] corto = {(byte) 0xFF, (byte) 0xD8};

        assertEquals(ErrorCode.FILE_TYPE_NOT_ALLOWED,
            assertThrows(StorageException.class,
                () -> validator.validar(archivo(corto, "t.jpg", "image/jpeg"))).getCode());
    }

    /**
     * "RIFF" solo no es WEBP: tambien lo usan WAV y AVI. Sin chequear los bytes
     * 8-11 se colaria cualquier contenedor RIFF.
     */
    @Test
    void rechazaRiffQueNoEsWebp() {
        byte[] wav = {'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'A', 'V', 'E'};

        assertEquals(ErrorCode.FILE_TYPE_NOT_ALLOWED,
            assertThrows(StorageException.class,
                () -> validator.validar(archivo(wav, "t.webp", "image/webp"))).getCode());
    }

    // -----------------------------------------------------------------------
    // Lo que se persiste sale del formato detectado, no del cliente
    // -----------------------------------------------------------------------

    @Test
    void elFormatoDetectadoDefineExtensionYContentType() {
        assertEquals(".jpg", ImageValidator.Formato.JPEG.extension());
        assertEquals("image/jpeg", ImageValidator.Formato.JPEG.contentType());
        assertEquals(".png", ImageValidator.Formato.PNG.extension());
        assertEquals("image/png", ImageValidator.Formato.PNG.contentType());
        assertEquals(".webp", ImageValidator.Formato.WEBP.extension());
        assertEquals("image/webp", ImageValidator.Formato.WEBP.contentType());
    }
}
