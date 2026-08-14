package com.retrorental.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.retrorental.backend.model.Administrador;
import com.retrorental.backend.model.Empleado;
import com.retrorental.backend.model.Persona;
import com.retrorental.backend.model.enums.Rol;
import com.retrorental.backend.repository.PersonaRepository;
import jakarta.servlet.FilterChain;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * El JwtFilter decide QUIEN es cada request. Los tests de controller usan
 * WithMockUser, que lo saltea por completo: si no se testea aca, no se testea
 * en ningun lado.
 *
 * Lo que se cubre es la diferencia entre "el token esta bien firmado" y "esta
 * persona todavia puede operar", que es justo lo que faltaba: antes el filtro
 * confiaba en el contenido del token sin mirar la base.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("auth")
class JwtFilterTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private PersonaRepository personaRepository;
    @Mock private FilterChain filterChain;

    @InjectMocks private JwtFilter filter;

    @AfterEach
    void limpiarContexto() {
        // El SecurityContext es un ThreadLocal: sin esto, la autenticacion de
        // un test se filtra al siguiente y los resultados dependen del orden.
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestConToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer un.token.valido");
        return request;
    }

    private void tokenValidoPara(String username) {
        when(jwtUtil.isTokenValid("un.token.valido")).thenReturn(true);
        when(jwtUtil.extractUsername("un.token.valido")).thenReturn(username);
    }

    private Empleado empleado(String username, LocalDate fechaBaja) {
        Empleado e = new Empleado();
        e.setUsername(username);
        e.setRol(Rol.EMPLEADO);
        e.setFechaBaja(fechaBaja);
        return e;
    }

    private Authentication autenticacion() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private void ejecutar(MockHttpServletRequest request) throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), filterChain);
    }

    // -----------------------------------------------------------------------
    // Camino feliz
    // -----------------------------------------------------------------------

    @Test
    void empleadoActivo_quedaAutenticado() throws Exception {
        tokenValidoPara("jperez");
        when(personaRepository.findByUsername("jperez"))
            .thenReturn(Optional.of(empleado("jperez", null)));

        ejecutar(requestConToken());

        Authentication auth = autenticacion();
        assertNotNull(auth);
        assertEquals("jperez", auth.getName());
        assertTrue(auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_EMPLEADO")));
        verify(filterChain).doFilter(any(), any());
    }

    // -----------------------------------------------------------------------
    // El corazon de SEC-06
    // -----------------------------------------------------------------------

    /**
     * Antes de este cambio, dar de baja a un empleado no le quitaba el acceso:
     * seguia operando con su token hasta que venciera. No habia forma de
     * expulsar a nadie en el momento.
     */
    @Test
    void empleadoDadoDeBaja_noQuedaAutenticadoAunqueElTokenSeaValido() throws Exception {
        tokenValidoPara("despedido");
        when(personaRepository.findByUsername("despedido"))
            .thenReturn(Optional.of(empleado("despedido", LocalDate.now())));

        ejecutar(requestConToken());

        assertNull(autenticacion());
        // La cadena sigue: es la config de seguridad la que responde 401.
        verify(filterChain).doFilter(any(), any());
    }

    /** Token de alguien cuya fila ya no existe: tampoco autentica. */
    @Test
    void personaInexistente_noQuedaAutenticada() throws Exception {
        tokenValidoPara("fantasma");
        when(personaRepository.findByUsername("fantasma")).thenReturn(Optional.empty());

        ejecutar(requestConToken());

        assertNull(autenticacion());
    }

    /**
     * El rol sale de la BASE, no del claim. Si viniera del token, degradar a
     * alguien de administrador a empleado no tendria efecto hasta que su token
     * venciera: seguiria entrando a /admin con el claim viejo.
     */
    @Test
    void elRolSaleDeLaBaseYNoDelClaimDelToken() throws Exception {
        tokenValidoPara("exadmin");
        when(personaRepository.findByUsername("exadmin"))
            .thenReturn(Optional.of(empleado("exadmin", null)));
        // El token todavia afirma que es administrador.
        when(jwtUtil.extractRol("un.token.valido")).thenReturn("ADMINISTRADOR");

        ejecutar(requestConToken());

        Authentication auth = autenticacion();
        assertNotNull(auth);
        assertTrue(auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_EMPLEADO")));
        assertTrue(auth.getAuthorities().stream()
            .noneMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR")));
    }

    /** Un administrador no tiene baja logica: siempre cuenta como activo. */
    @Test
    void administrador_quedaAutenticado() throws Exception {
        tokenValidoPara("jefe");
        Administrador a = new Administrador();
        a.setUsername("jefe");
        a.setRol(Rol.ADMINISTRADOR);
        when(personaRepository.findByUsername("jefe")).thenReturn(Optional.of((Persona) a));

        ejecutar(requestConToken());

        assertTrue(autenticacion().getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMINISTRADOR")));
    }

    // -----------------------------------------------------------------------
    // Casos que ya existian y no deben cambiar
    // -----------------------------------------------------------------------

    @Test
    void sinHeaderAuthorization_pasaSinAutenticarYNoConsultaLaBase() throws Exception {
        ejecutar(new MockHttpServletRequest());

        assertNull(autenticacion());
        verify(personaRepository, never()).findByUsername(any());
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void tokenInvalido_pasaSinAutenticarYNoConsultaLaBase() throws Exception {
        when(jwtUtil.isTokenValid("un.token.valido")).thenReturn(false);

        ejecutar(requestConToken());

        assertNull(autenticacion());
        verify(personaRepository, never()).findByUsername(any());
    }

    @Test
    void headerQueNoEsBearer_pasaSinAutenticar() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        ejecutar(request);

        assertNull(autenticacion());
        verify(personaRepository, never()).findByUsername(any());
    }
}
