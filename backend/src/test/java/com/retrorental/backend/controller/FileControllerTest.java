package com.retrorental.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.retrorental.backend.config.SecurityConfig;
import com.retrorental.backend.security.JwtFilter;
import com.retrorental.backend.service.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Tests de /files (subida y URL presignada). Requieren estar autenticado.
 */
@WebMvcTest(controllers = FileController.class)
@Import({SecurityConfig.class, JwtFilter.class})
class FileControllerTest extends AbstractControllerTest {

    @MockitoBean
    private StorageService storageService;

    @Test
    void upload_sinToken_devuelve401() throws Exception {
        var file = new MockMultipartFile("file", "f.jpg", "image/jpeg", "x".getBytes());

        mockMvc.perform(multipart("/files").file(file))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void upload_autenticado_devuelve200() throws Exception {
        when(storageService.upload(any(), anyString())).thenReturn("tickets/uuid.jpg");
        when(storageService.getUrl("tickets/uuid.jpg")).thenReturn("http://url/tickets/uuid.jpg");

        var file = new MockMultipartFile("file", "f.jpg", "image/jpeg", "x".getBytes());

        mockMvc.perform(multipart("/files").file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.objectKey").value("tickets/uuid.jpg"))
            .andExpect(jsonPath("$.url").value("http://url/tickets/uuid.jpg"));
    }

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void url_autenticado_devuelve200() throws Exception {
        when(storageService.getUrl(eq("tickets/uuid.jpg")))
            .thenReturn("http://url/tickets/uuid.jpg");

        mockMvc.perform(get("/files/url").param("key", "tickets/uuid.jpg"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.objectKey").value("tickets/uuid.jpg"));
    }

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void url_sinParamKey_devuelve400() throws Exception {
        mockMvc.perform(get("/files/url"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
            .andExpect(jsonPath("$.field").value("key"));
    }
}
