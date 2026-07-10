package com.retrorental.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.retrorental.backend.config.SecurityConfig;
import com.retrorental.backend.dto.request.DireccionRequest;
import com.retrorental.backend.dto.request.LoginRequest;
import com.retrorental.backend.dto.request.RegisterRequest;
import com.retrorental.backend.dto.request.TelefonoRequest;
import com.retrorental.backend.dto.response.AuthResponse;
import com.retrorental.backend.exception.ConflictException;
import com.retrorental.backend.exception.ErrorCode;
import com.retrorental.backend.exception.InvalidCredentialsException;
import com.retrorental.backend.model.enums.Rol;
import com.retrorental.backend.security.JwtFilter;
import com.retrorental.backend.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Tests de la capa web de /auth/register y /auth/login. Endpoints publicos, sin
 * seguridad, pero sujetos a validacion @Valid.
 */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, JwtFilter.class})
class AuthControllerTest extends AbstractControllerTest {

    @MockitoBean
    private AuthService authService;

    private RegisterRequest validRegister() {
        RegisterRequest req = new RegisterRequest();
        req.setNombre("Juan");
        req.setApellido("Perez");
        req.setDocumento("30123456");
        req.setEmail("juan@example.com");
        req.setPassword("password123");
        req.setRol(Rol.EMPLEADO);

        DireccionRequest dir = new DireccionRequest();
        dir.setCalle("San Martin");
        dir.setNumero("123");
        dir.setCiudad("Cordoba");
        dir.setProvincia("Cordoba");
        dir.setCodigoPostal("5000");
        dir.setBarrio("Centro");
        req.setDireccion(dir);

        TelefonoRequest tel = new TelefonoRequest();
        tel.setCodigoArea("351");
        tel.setNumero("1234567");
        req.setTelefono(tel);
        return req;
    }

    @Test
    void register_devuelve200_conDatosValidos() throws Exception {
        when(authService.register(any())).thenReturn(
            new AuthResponse("token-abc", "Juan", "Perez", "juan@example.com", Rol.EMPLEADO, "342 5551234"));

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegister())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("token-abc"))
            .andExpect(jsonPath("$.rol").value("EMPLEADO"));
    }

    @Test
    void register_devuelve400_conEmailInvalidoYPasswordCorta() throws Exception {
        RegisterRequest req = validRegister();
        req.setEmail("no-es-un-email");
        req.setPassword("123");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void register_devuelve409_conEmailDuplicado() throws Exception {
        when(authService.register(any())).thenThrow(new ConflictException(
            ErrorCode.EMAIL_ALREADY_EXISTS, "Ya existe un usuario con ese email", "email"));

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegister())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
            .andExpect(jsonPath("$.field").value("email"));
    }

    @Test
    void login_devuelve200_conCredencialesValidas() throws Exception {
        when(authService.login(any())).thenReturn(
            new AuthResponse("token-abc", "Juan", "Perez", "juan@example.com", Rol.ADMINISTRADOR, "342 5551234"));

        LoginRequest req = new LoginRequest();
        req.setEmail("juan@example.com");
        req.setPassword("password123");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("token-abc"))
            .andExpect(jsonPath("$.rol").value("ADMINISTRADOR"));
    }

    @Test
    void login_devuelve401_conCredencialesIncorrectas() throws Exception {
        when(authService.login(any())).thenThrow(
            new InvalidCredentialsException("Email o contraseña incorrectos"));

        LoginRequest req = new LoginRequest();
        req.setEmail("juan@example.com");
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
