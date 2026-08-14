package com.retrorental.backend.service;

import com.retrorental.backend.exception.ErrorCode;
import com.retrorental.backend.exception.StorageException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Verifica que un archivo subido sea REALMENTE una imagen, mirando sus primeros
 * bytes.
 *
 * Por que no alcanza con el Content-Type del request: ese header lo escribe
 * quien hace la peticion, no el archivo. No es una propiedad del contenido, es
 * una etiqueta que eligio el emisor. De hecho la propia app mobile la manda
 * hardcodeada ("type: 'image/jpeg'" en services/tickets.ts), sin mirar el
 * archivo: prueba de que el valor no significa nada. Un cliente hecho a mano
 * declara lo que quiera.
 *
 * Que pasaba sin esto: alguien sube un HTML declarado como text/html, con
 * extension .html tomada del nombre que el mismo eligio. MinIO lo guarda con
 * ese content-type y nginx lo sirve tal cual desde files.<dominio>, asi que el
 * navegador lo EJECUTA. Es XSS almacenado en el propio dominio donde viven las
 * URLs presignadas de todas las fotos.
 *
 * Los magic bytes, en cambio, son parte del archivo: no se pueden falsificar
 * sin dejar de ser una imagen valida.
 */
@Component
public class ImageValidator {

    /** Con 12 bytes alcanza para las tres firmas (WEBP es la mas larga). */
    private static final int BYTES_DE_FIRMA = 12;

    /**
     * Formatos aceptados. Son los que producen la camara y la galeria de la app
     * (expo-camera devuelve JPEG; expo-image-picker con quality reencodea a
     * JPEG, asi que el HEIC de iPhone no llega hasta aca).
     *
     * La lista es deliberadamente corta: cada formato agregado es superficie
     * nueva. Si algun dia hace falta otro, se suma a conciencia y con su firma.
     */
    public enum Formato {
        JPEG(".jpg", "image/jpeg"),
        PNG(".png", "image/png"),
        WEBP(".webp", "image/webp");

        private final String extension;
        private final String contentType;

        Formato(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        /** Extension REAL, derivada del contenido y no del nombre del cliente. */
        public String extension() {
            return extension;
        }

        /** Content-type REAL, derivado del contenido y no del header del request. */
        public String contentType() {
            return contentType;
        }
    }

    /**
     * Devuelve el formato detectado, o corta con 400 si el archivo no es una de
     * las imagenes permitidas.
     *
     * Lee solo la cabecera: no carga el archivo entero en memoria. El stream se
     * abre y se cierra aca; el llamador pide uno nuevo para subirlo.
     */
    public Formato validar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new StorageException(ErrorCode.FILE_EMPTY, "El archivo esta vacio");
        }

        byte[] firma = leerFirma(file);
        Formato formato = detectar(firma);

        if (formato == null) {
            throw new StorageException(ErrorCode.FILE_TYPE_NOT_ALLOWED,
                "El archivo no es una imagen valida. Se aceptan JPG, PNG o WEBP");
        }

        return formato;
    }

    private byte[] leerFirma(MultipartFile file) {
        try (InputStream stream = file.getInputStream()) {
            return stream.readNBytes(BYTES_DE_FIRMA);
        } catch (IOException e) {
            throw new StorageException(
                ErrorCode.STORAGE_ERROR, "No se pudo leer el archivo subido", e);
        }
    }

    /**
     * Un archivo mas corto que su firma no puede coincidir con ninguna, asi que
     * los chequeos de longitud son parte de la deteccion, no un caso aparte.
     */
    private Formato detectar(byte[] b) {
        if (esJpeg(b)) {
            return Formato.JPEG;
        }
        if (esPng(b)) {
            return Formato.PNG;
        }
        if (esWebp(b)) {
            return Formato.WEBP;
        }
        return null;
    }

    // FF D8 FF — comun a todas las variantes de JPEG (JFIF, Exif, ...).
    private boolean esJpeg(byte[] b) {
        return b.length >= 3
            && (b[0] & 0xFF) == 0xFF
            && (b[1] & 0xFF) == 0xD8
            && (b[2] & 0xFF) == 0xFF;
    }

    // 89 'P' 'N' 'G' 0D 0A 1A 0A — los 8 bytes de firma del estandar.
    private static final byte[] FIRMA_PNG = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private boolean esPng(byte[] b) {
        return b.length >= FIRMA_PNG.length
            && Arrays.equals(Arrays.copyOfRange(b, 0, FIRMA_PNG.length), FIRMA_PNG);
    }

    /**
     * WEBP es un contenedor RIFF: "RIFF" en 0-3, el tamano en 4-7 (que varia
     * por archivo y por eso se saltea) y "WEBP" en 8-11.
     */
    private boolean esWebp(byte[] b) {
        return b.length >= 12
            && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
            && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }
}
