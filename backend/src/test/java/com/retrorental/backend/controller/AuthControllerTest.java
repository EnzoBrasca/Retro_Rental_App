package com.retrorental.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.retrorental.backend.config.SecurityConfig;
import com.retrorental.backend.dto.request.LoginRequest;
import com.retrorental.backend.dto.request.RegisterRequest;
import com.retrorental.backend.dto.request.TelefonoRequest;
import com.retrorental.backend.dto.response.AuthResponse;
import com.retrorental.backend.exception.ConflictException;
import com.retrorental.backend.exception.ErrorCode;
import com.retrorental.backend.exception.InvalidCredentialsException;
import com.retrorental.backend.model.enums.Rol;
import com.retrorental.backend.security.JwtFilter;
import com.retrorental.backend.security.LoginRateLimitFilter;
import com.retrorental.backend.service.AuthService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Tests de la capa web de /auth/register y /auth/login. Endpoints publicos, sin
 * seguridad, pero sujetos a validacion @Valid.
 */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, JwtFilter.class, LoginRateLimitFilter.class})
// El limite de intentos se sube bien alto para que no interfiera: aca se
// ejerce el contrato de /auth, y el rate limit tiene su propio test.
@TestPropertySource(properties = "app.rate-limit.login.max-attempts=1000")
@Tag("auth")
class AuthControllerTest extends AbstractControllerTest {

    @MockitoBean
    private AuthService authService;

    private RegisterRequest validRegister() {
        RegisterRequest req = new RegisterRequest();
        req.setNombre("Juan");
        req.setApellido("Perez");
        req.setDocumento("30123456");
        req.setPassword("password123");

        TelefonoRequest tel = new TelefonoRequest();
        tel.setCodigoArea("351");
        tel.setNumero("1234567");
        req.setTelefono(tel);
        return req;
    }

    @Test
    void register_devuelve200_conDatosValidos() throws Exception {
        when(authService.register(any())).thenReturn(
            new AuthResponse("token-abc", "Juan", "Perez", "juanperez", Rol.EMPLEADO, "342 5551234", 1_700_000_000_000L));

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegister())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("token-abc"))
            .andExpect(jsonPath("$.username").value("juanperez"))
            .andExpect(jsonPath("$.rol").value("EMPLEADO"));
    }

    @Test
    void register_devuelve400_conPasswordCorta() throws Exception {
        RegisterRequest req = validRegister();
        req.setPassword("123");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void register_devuelve409_conDocumentoDuplicado() throws Exception {
        when(authService.register(any())).thenThrow(new ConflictException(
            ErrorCode.DOCUMENTO_ALREADY_EXISTS, "Ya existe un usuario con ese documento", "documento"));

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegister())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DOCUMENTO_ALREADY_EXISTS"))
            .andExpect(jsonPath("$.field").value("documento"));
    }

    @Test
    void login_devuelve200_conCredencialesValidas() throws Exception {
        when(authService.login(any())).thenReturn(
            new AuthResponse("token-abc", "Juan", "Perez", "juanperez", Rol.ADMINISTRADOR, "342 5551234", 1_700_000_000_000L));

        LoginRequest req = new LoginRequest();
        req.setUsername("juanperez");
        req.setPassword("password123");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("token-abc"))
            .andExpect(jsonPath("$.rol").value("ADMINISTRADOR"))
            .andExpect(jsonPath("$.expiresAt").value(1_700_000_000_000L));
    }

    @Test
    void login_devuelve401_conCredencialesIncorrectas() throws Exception {
        when(authService.login(any())).thenThrow(
            new InvalidCredentialsException("Usuario o contraseña incorrectos"));

        LoginRequest req = new LoginRequest();
        req.setUsername("juanperez");
        req.setPassword("wrong");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void login_devuelve400_conCamposVacios() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
