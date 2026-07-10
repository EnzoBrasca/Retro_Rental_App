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

    public MinioStorageService(MinioClient minioClient,
                               @Qualifier("minioPublicClient") MinioClient publicClient,
                               MinioProperties properties) {
        this.minioClient = minioClient;
        this.publicClient = publicClient;
        this.properties = properties;
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

    @Override
    public String upload(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new StorageException(ErrorCode.FILE_EMPTY, "El archivo esta vacio");
        }
        String objectKey = buildObjectKey(folder, file.getOriginalFilename());
        try (InputStream stream = file.getInputStream()) {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .stream(stream, file.getSize(), -1)
                    .contentType(file.getContentType())
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

    // Genera una key unica conservando la extension original del archivo.
    private String buildObjectKey(String folder, String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }
        return "%s/%s%s".formatted(folder, UUID.randomUUID(), extension);
    }
}
