package com.retrorental.backend.controller;

import com.retrorental.backend.dto.response.FileUploadResponse;
import com.retrorental.backend.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final StorageService storageService;

    // Sube una imagen (multipart/form-data) y devuelve su key + URL presignada.
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "tickets") String folder) {
        String objectKey = storageService.upload(file, folder);
        return ResponseEntity.ok(new FileUploadResponse(objectKey, storageService.getUrl(objectKey)));
    }

    // Devuelve una URL presignada fresca para una key ya almacenada.
    @GetMapping("/url")
    public ResponseEntity<FileUploadResponse> url(@RequestParam("key") String objectKey) {
        return ResponseEntity.ok(new FileUploadResponse(objectKey, storageService.getUrl(objectKey)));
    }
}
