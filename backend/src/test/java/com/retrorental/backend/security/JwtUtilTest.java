package com.retrorental.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * JwtUtil es lo unico que separa "hay sesion valida" de "cualquiera entra".
 * JwtFilterTest lo mockea por completo, asi que la firma y validacion real
 * (Jwts.builder / Jwts.parser) no tenian ningun test que las ejercitara.
 */
@Tag("security")
class JwtUtilTest {

    private static final String SECRET =
        "secret-de-test-que-no-se-usa-en-ningun-entorno-real";

    private JwtUtil jwtUtilConSecretYExpiracion(String secret, long expiration) {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", secret);
        ReflectionTestUtils.setField(jwtUtil, "expiration", expiration);
        return jwtUtil;
    }

    private JwtUtil jwtUtil() {
        return jwtUtilConSecretYExpiracion(SECRET, 3_600_000L);
    }

    // ------------------------------------------------------ camino feliz

    @Test
    void generateToken_extractUsername_devuelveElUsernameIntacto() {
        String token = jwtUtil().generateToken("juan.perez", "EMPLEADO");

        assertEquals("juan.perez", jwtUtil().extractUsername(token));
    }

    @Test
    void generateToken_extractRol_devuelveElRolIntacto() {
        String token = jwtUtil().generateToken("juan.perez", "ADMINISTRADOR");

        assertEquals("ADMINISTRADOR", jwtUtil().extractRol(token));
    }

    @Test
    void generateToken_extractExpirationMillis_esCoherenteConLaExpiracionConfigurada() {
        long expiration = 3_600_000L;
        JwtUtil jwtUtil = jwtUtilConSecretYExpiracion(SECRET, expiration);

        long antes = System.currentTimeMillis();
        String token = jwtUtil.generateToken("juan.perez", "EMPLEADO");
        long despues = System.currentTimeMillis();

        long expiracion = jwtUtil.extractExpirationMillis(token);

        // El limite inferior descuenta un segundo A PROPOSITO. El claim `exp` de
        // un JWT se guarda en SEGUNDOS (RFC 7519), asi que jjwt trunca hacia
        // abajo al firmar: el valor que vuelve puede ser hasta 999 ms menor que
        // el instante real de la firma. El nombre extractExpirationMillis
        // promete una precision que el formato no da.
        //
        // No es un bug —un token que vence un segundo antes vence de mas, nunca
        // de menos, que es el lado seguro— pero si se compara este valor contra
        // System.currentTimeMillis() en algun lado, hay que saber que arrastra
        // ese error.
        assertTrue(expiracion >= antes + expiration - 1000);
        assertTrue(expiracion <= despues + expiration);
    }

    @Test
    void isTokenValid_tokenRecienGenerado_esValido() {
        JwtUtil jwtUtil = jwtUtil();
        String token = jwtUtil.generateToken("juan.perez", "EMPLEADO");

        assertTrue(jwtUtil.isTokenValid(token));
    }

    // ------------------------------------------------------ rechazo

    @Test
    void isTokenValid_firmaAlterada_esInvalido() {
        JwtUtil jwtUtil = jwtUtil();
        String token = jwtUtil.generateToken("juan.perez", "EMPLEADO");
        String tokenAlterado = token.substring(0, token.length() - 1)
            + (token.charAt(token.length() - 1) == 'a' ? 'b' : 'a');

        assertFalse(jwtUtil.isTokenValid(tokenAlterado));
    }

    @Test
    void isTokenValid_firmadoConOtraClave_esInvalido() {
        String token = jwtUtilConSecretYExpiracion(
            "otro-secret-de-test-que-tampoco-se-usa-en-produccion", 3_600_000L)
            .generateToken("juan.perez", "EMPLEADO");

        assertFalse(jwtUtil().isTokenValid(token));
    }

    @Test
    void isTokenValid_tokenExpirado_esInvalido() {
        JwtUtil jwtUtil = jwtUtilConSecretYExpiracion(SECRET, -1_000L);
        String token = jwtUtil.generateToken("juan.perez", "EMPLEADO");

        assertFalse(jwtUtil.isTokenValid(token));
    }

    @Test
    void isTokenValid_basura_esInvalido() {
        JwtUtil jwtUtil = jwtUtil();

        assertFalse(jwtUtil.isTokenValid("esto-no-es-un-jwt"));
        assertFalse(jwtUtil.isTokenValid(""));
    }

    @Test
    void extractUsername_tokenInvalido_propagaLaExcepcion() {
        JwtUtil jwtUtil = jwtUtil();

        assertThrows(JwtException.class, () -> jwtUtil.extractUsername("esto-no-es-un-jwt"));
    }

    @Test
    void extractRol_tokenInvalido_propagaLaExcepcion() {
        JwtUtil jwtUtil = jwtUtil();

        assertThrows(JwtException.class, () -> jwtUtil.extractRol("esto-no-es-un-jwt"));
    }
}
