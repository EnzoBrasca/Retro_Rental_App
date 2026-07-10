package com.retrorental.backend.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    // Sube un archivo y devuelve la object key con la que quedo almacenado.
    String upload(MultipartFile file, String folder);

    // Genera una URL presignada (temporal) para descargar el objeto.
    String getUrl(String objectKey);

    // Elimina un objeto del almacenamiento.
    void delete(String objectKey);
}
