package com.retrorental.backend.service;

import com.retrorental.backend.exception.ConflictException;
import com.retrorental.backend.exception.ErrorCode;
import com.retrorental.backend.model.Precio;
import com.retrorental.backend.model.enums.TipoCombustible;
import com.retrorental.backend.repository.PrecioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Banda de validacion del ALTA de un precio: el caso en el que un proveedor
 * todavia no tiene vigente de ese combustible y el valor entra al catalogo sin
 * nada contra que compararlo.
 *
 * validarMargen cubre la CORRECCION (hay un vigente del mismo proveedor). Estos
 * tests cubren el hueco complementario: el primer precio de la combinacion, que
 * hasta ahora entraba al catalogo sin control alguno.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrecioCatalogoServiceTest {

    @Mock private PrecioRepository precioRepository;

    private PrecioCatalogoService service;

    private static final TipoCombustible GASOIL = TipoCombustible.GASOIL_GRADO_2;

    @BeforeEach
    void setUp() {
        service = new PrecioCatalogoService(precioRepository);
        ReflectionTestUtils.setField(service, "margenMaximo", new BigDecimal("30"));
    }

    /** Deja el catalogo con vigentes de GASOIL entre min y max. */
    private void catalogoConBanda(String min, String max) {
        when(precioRepository
            .findFirstByTipoCombustibleAndFechaHastaIsNullOrderByPrecioUnitarioAsc(GASOIL))
            .thenReturn(Optional.of(precioDe(min)));
        when(precioRepository
            .findFirstByTipoCombustibleAndFechaHastaIsNullOrderByPrecioUnitarioDesc(GASOIL))
            .thenReturn(Optional.of(precioDe(max)));
    }

    private Precio precioDe(String valor) {
        Precio p = new Precio();
        p.setPrecioUnitario(new BigDecimal(valor));
        p.setTipoCombustible(GASOIL);
        return p;
    }

    // ------------------------------------------------------------ dentro

    @Test
    void dentroDeLaBanda_noLanza() {
        catalogoConBanda("2000.00", "2100.00");

        assertDoesNotThrow(() ->
            service.validarAltaInicial(new BigDecimal("2050.00"), GASOIL, "precioUnitario"));
    }

    @Test
    void justoEnElTecho_noLanza() {
        catalogoConBanda("2000.00", "2100.00");

        // 2100 + 30 = 2130 es el ultimo valor aceptado, no el primero rechazado.
        assertDoesNotThrow(() ->
            service.validarAltaInicial(new BigDecimal("2130.00"), GASOIL, "precioUnitario"));
    }

    @Test
    void justoEnElPiso_noLanza() {
        catalogoConBanda("2000.00", "2100.00");

        assertDoesNotThrow(() ->
            service.validarAltaInicial(new BigDecimal("1970.00"), GASOIL, "precioUnitario"));
    }

    // ------------------------------------------------------------ fuera

    @Test
    void porEncimaDelTecho_lanzaFueraDeRango() {
        catalogoConBanda("2000.00", "2100.00");

        ConflictException ex = assertThrows(ConflictException.class, () ->
            service.validarAltaInicial(new BigDecimal("2130.01"), GASOIL, "precioUnitario"));

        assertEquals(ErrorCode.PRECIO_FUERA_DE_RANGO, ex.getCode());
    }

    @Test
    void unCeroDeMas_lanzaFueraDeRango() {
        catalogoConBanda("2000.00", "2100.00");

        // El caso que motiva toda esta banda: 20860 en vez de 2086.
        assertThrows(ConflictException.class, () ->
            service.validarAltaInicial(new BigDecimal("20860.00"), GASOIL, "precioUnitario"));
    }

    @Test
    void porDebajoDelPiso_lanzaFueraDeRango() {
        catalogoConBanda("2000.00", "2100.00");

        // Un techo solo no alcanza: tipear 12 en vez de 1200 lo pasaria limpio.
        assertThrows(ConflictException.class, () ->
            service.validarAltaInicial(new BigDecimal("1969.99"), GASOIL, "precioUnitario"));
    }

    // ------------------------------------------------------- sin referencia

    @Test
    void sinVigentesDeEseCombustible_acepta() {
        when(precioRepository
            .findFirstByTipoCombustibleAndFechaHastaIsNullOrderByPrecioUnitarioAsc(GASOIL))
            .thenReturn(Optional.empty());
        when(precioRepository
            .findFirstByTipoCombustibleAndFechaHastaIsNullOrderByPrecioUnitarioDesc(GASOIL))
            .thenReturn(Optional.empty());

        // No hay banda que construir. No se inventa un rango: se acepta, igual
        // que fueraDeMargen cuando no hay vigente.
        assertDoesNotThrow(() ->
            service.validarAltaInicial(new BigDecimal("999999.00"), GASOIL, "precioUnitario"));
    }

    @Test
    void propuestoNull_acepta() {
        catalogoConBanda("2000.00", "2100.00");

        assertDoesNotThrow(() -> service.validarAltaInicial(null, GASOIL, "precioUnitario"));
        verifyNoInteractions(precioRepository);
    }

    @Test
    void combustibleNull_acepta() {
        assertDoesNotThrow(() ->
            service.validarAltaInicial(new BigDecimal("2050.00"), null, "precioUnitario"));
        verifyNoInteractions(precioRepository);
    }

    // ------------------------------------------------------ por combustible

    @Test
    void laBandaSeConstruyeSoloConElMismoCombustible() {
        catalogoConBanda("2000.00", "2100.00");

        service.fueraDeBandaDeAlta(new BigDecimal("2050.00"), GASOIL);

        // Que el techo del gasoil salga de los gasoiles y no del maximo global:
        // si no, la nafta premium de la estacion mas cara le habilita cualquier
        // disparate al gasoil.
        verify(precioRepository)
            .findFirstByTipoCombustibleAndFechaHastaIsNullOrderByPrecioUnitarioDesc(GASOIL);
        verify(precioRepository)
            .findFirstByTipoCombustibleAndFechaHastaIsNullOrderByPrecioUnitarioAsc(GASOIL);
    }

    @Test
    void laMezclaNoQuedaExenta() {
        when(precioRepository
            .findFirstByTipoCombustibleAndFechaHastaIsNullOrderByPrecioUnitarioAsc(
                TipoCombustible.MEZCLA))
            .thenReturn(Optional.of(precioDe("3000.00")));
        when(precioRepository
            .findFirstByTipoCombustibleAndFechaHastaIsNullOrderByPrecioUnitarioDesc(
                TipoCombustible.MEZCLA))
            .thenReturn(Optional.of(precioDe("3200.00")));

        // fueraDeMargen SI exime a la MEZCLA, porque ahi se la compara contra el
        // vigente de NAFTA_SUPER, del que se aleja legitimamente. Aca la
        // referencia son OTRAS MEZCLAS: la comparacion es valida y el tope rige.
        assertTrue(service.fueraDeBandaDeAlta(
            new BigDecimal("32000.00"), TipoCombustible.MEZCLA));
    }

    // ----------------------------------------------- variante no lanzadora

    @Test
    void fueraDeBandaDeAlta_noLanza_soloInforma() {
        catalogoConBanda("2000.00", "2100.00");

        assertTrue(service.fueraDeBandaDeAlta(new BigDecimal("20860.00"), GASOIL));
        assertFalse(service.fueraDeBandaDeAlta(new BigDecimal("2050.00"), GASOIL));
    }
}
