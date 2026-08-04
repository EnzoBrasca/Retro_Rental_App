package com.retrorental.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.retrorental.backend.config.SecurityConfig;
import com.retrorental.backend.dto.request.LoginRequest;
import com.retrorental.backend.exception.InvalidCredentialsException;
import com.retrorental.backend.security.JwtFilter;
import com.retrorental.backend.security.LoginRateLimitFilter;
import com.retrorental.backend.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Tests del freno de fuerza bruta sobre POST /auth/login.
 *
 * El limite se baja a 3 intentos para no tener que disparar decenas de
 * requests. La ventana se deja larga a proposito: si venciera durante el test,
 * el contador se reiniciaria y el resultado dependeria del reloj.
 */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, JwtFilter.class, LoginRateLimitFilter.class})
@TestPropertySource(properties = {
    "app.rate-limit.login.max-attempts=3",
    "app.rate-limit.login.window-seconds=600"
})
class LoginRateLimitFilterTest extends AbstractControllerTest {

    @MockitoBean
    private AuthService authService;

    /** Un intento de login fallido desde la IP indicada. */
    private MockHttpServletRequestBuilder intentoDesde(String ip) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("juanperez");
        req.setPassword("wrong");

        return post("/auth/login")
            .with(request -> { request.setRemoteAddr(ip); return request; })
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req));
    }

    @Test
    void devuelve429_alSuperarElLimiteDeIntentos() throws Exception {
        when(authService.login(any())).thenThrow(
            new InvalidCredentialsException("Usuario o contraseña incorrectos"));

        // Los 3 primeros pasan el filtro y llegan al servicio: fallan por
        // credenciales, no por rate limit.
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(intentoDesde("10.0.0.1"))
                .andExpect(status().isUnauthorized());
        }

        // El cuarto ya no llega: lo corta el filtro.
        mockMvc.perform(intentoDesde("10.0.0.1"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
            .andExpect(header().string("Retry-After", "600"));
    }

    @Test
    void alBloquear_noLlegaAlServicioDeAutenticacion() throws Exception {
        when(authService.login(any())).thenThrow(
            new InvalidCredentialsException("Usuario o contraseña incorrectos"));

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(intentoDesde("10.0.0.2"));
        }
        // Consumido el cupo, el request bloqueado no debe costar una
        // verificacion de contrasena (bcrypt es caro a proposito).
        verify(authService, org.mockito.Mockito.times(3)).login(any());

        mockMvc.perform(intentoDesde("10.0.0.2"))
            .andExpect(status().isTooManyRequests());

        verify(authService, org.mockito.Mockito.times(3)).login(any());
    }

    @Test
    void elLimiteEsPorIp_unaIpBloqueadaNoAfectaAOtra() throws Exception {
        when(authService.login(any())).thenThrow(
            new InvalidCredentialsException("Usuario o contraseña incorrectos"));

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(intentoDesde("10.0.0.3"));
        }
        mockMvc.perform(intentoDesde("10.0.0.3"))
            .andExpect(status().isTooManyRequests());

        // Otro cliente, con su propio contador, sigue pudiendo intentar. Esto
        // es lo que evita que un atacante deje afuera a todos los empleados.
        mockMvc.perform(intentoDesde("10.0.0.4"))
            .andExpect(status().isUnauthorized());
    }

    /**
     * El registro es publico a proposito, asi que tambien necesita freno: sin
     * el, cualquiera crea cuentas en masa contra la base.
     */
    @Test
    void devuelve429_alSuperarElLimiteEnElRegistro() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/auth/register")
                .with(request -> { request.setRemoteAddr("10.0.0.6"); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
        }

        mockMvc.perform(post("/auth/register")
                .with(request -> { request.setRemoteAddr("10.0.0.6"); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    /** Login y registro comparten el contador: es la misma IP golpeando /auth. */
    @Test
    void elContadorEsCompartidoEntreLoginYRegistro() throws Exception {
        when(authService.login(any())).thenThrow(
            new InvalidCredentialsException("Usuario o contraseña incorrectos"));

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/auth/register")
                .with(request -> { request.setRemoteAddr("10.0.0.7"); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
        }

        mockMvc.perform(intentoDesde("10.0.0.7"))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void noLimitaOtrosEndpoints() throws Exception {
        // /health no pasa por el filtro por mas que se lo golpee de mas.
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/health").with(request -> {
                request.setRemoteAddr("10.0.0.5");
                return request;
            }));
        }

        // Y el login de esa misma IP sigue con su cupo intacto.
        when(authService.login(any())).thenThrow(
            new InvalidCredentialsException("Usuario o contraseña incorrectos"));

        mockMvc.perform(intentoDesde("10.0.0.5"))
            .andExpect(status().isUnauthorized());
    }
}
