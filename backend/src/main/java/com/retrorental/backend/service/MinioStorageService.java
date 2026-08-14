package com.retrorental.backend.service;

import com.retrorental.backend.config.MinioProperties;
import com.retrorental.backend.exception.ErrorCode;
import com.retrorental.backend.exception.StorageException;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class MinioStorageService implements StorageService {

    private final MinioClient minioClient;
    private final MinioClient publicClient;
    private final MinioProperties properties;
    private final ImageValidator imageValidator;

    public MinioStorageService(MinioClient minioClient,
                               @Qualifier("minioPublicClient") MinioClient publicClient,
                               MinioProperties properties,
                               ImageValidator imageValidator) {
        this.minioClient = minioClient;
        this.publicClient = publicClient;
        this.properties = properties;
        this.imageValidator = imageValidator;
    }

    // Crea el bucket si todavia no existe, al arrancar la app.
    @PostConstruct
    public void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(properties.getBucket()).build());
            if (!exists) {
                minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(properties.getBucket()).build());
            }
        } catch (Exception e) {
            throw new StorageException(
                ErrorCode.STORAGE_ERROR, "No se pudo inicializar el bucket de MinIO", e);
        }
    }

    /**
     * Sube una imagen y devuelve su object key.
     *
     * SOLO acepta imagenes, y lo decide mirando los magic bytes del contenido
     * (ver ImageValidator). Tanto la extension como el content-type con que se
     * guarda salen del formato DETECTADO, nunca de lo que mando el cliente:
     * antes se tomaban de getOriginalFilename() y getContentType(), que son dos
     * campos que el emisor escribe a mano. Con eso, un HTML declarado como
     * text/html quedaba guardado como .html con ese tipo, y nginx lo servia
     * desde files.<dominio> para que el navegador lo ejecutara.
     */
    @Override
    public String upload(MultipartFile file, String folder) {
        ImageValidator.Formato formato = imageValidator.validar(file);

        String objectKey = buildObjectKey(folder, formato);
        try (InputStream stream = file.getInputStream()) {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .stream(stream, file.getSize(), -1)
                    .contentType(formato.contentType())
                    .build());
            return objectKey;
        } catch (Exception e) {
            throw new StorageException(
                ErrorCode.STORAGE_ERROR, "No se pudo subir el archivo a MinIO", e);
        }
    }

    @Override
    public String getUrl(String objectKey) {
        try {
            return publicClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .expiry(properties.getPresignedExpirySeconds(), TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            throw new StorageException(
                ErrorCode.STORAGE_ERROR, "No se pudo generar la URL del objeto", e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new StorageException(
                ErrorCode.STORAGE_ERROR, "No se pudo eliminar el objeto de MinIO", e);
        }
    }

    /**
     * Genera una key unica con la extension del formato DETECTADO.
     *
     * El nombre que manda el cliente no se usa para nada: ni para la extension
     * ni para la key. Ademas de la extension enganosa, un originalFilename
     * controlado por el emisor es el vector clasico de path traversal
     * ("../../algo"). El UUID lo elimina de raiz.
     */
    private String buildObjectKey(String folder, ImageValidator.Formato formato) {
        return "%s/%s%s".formatted(folder, UUID.randomUUID(), formato.extension());
    }
}
