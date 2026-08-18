package com.retrorental.backend.service;

import com.retrorental.backend.dto.response.TicketAnalysisResponse;
import com.retrorental.backend.dto.response.TicketAnalysisResult;
import com.retrorental.backend.model.Precio;
import com.retrorental.backend.model.Proveedor;
import com.retrorental.backend.model.enums.Servicio;
import com.retrorental.backend.model.enums.TipoCombustible;
import com.retrorental.backend.repository.PrecioRepository;
import com.retrorental.backend.repository.ProveedorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Resolución de la salida del OCR contra el catálogo.
 *
 * POR QUE ESTE ARCHIVO. Al medir la cobertura por primera vez (JaCoCo, fase 3)
 * esta clase apareció al 23,7%: es la peor cubierta de todas las que tienen
 * lógica de negocio real. Estuvo escondida durante toda la auditoría porque
 * vivía adentro de TicketService, donde el número global la tapaba; recién al
 * extraerla (fase 2) quedó a la vista. Es justamente lo que la auditoría
 * esperaba de tener medición.
 *
 * Y lo que hay acá adentro importa: este código DA DE ALTA proveedores y
 * PISA precios del catálogo a partir de lo que un modelo de OCR creyó leer en
 * una foto sacada en el yacimiento. El freno a las alucinaciones (un 2.086
 * leído como 20.860 es un error de una coma) es lo único que separa una lectura
 * dudosa de que ese número quede como el precio vigente para TODOS los
 * empleados.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("ticket")
class CatalogoOcrResolverTest {

    @Mock private ProveedorRepository proveedorRepository;
    @Mock private PrecioRepository precioRepository;

    private CatalogoOcrResolver resolver;

    private Proveedor ypf;
    private Precio vigente;

    private static final BigDecimal PRECIO_VIGENTE = new BigDecimal("2086.00");

    @BeforeEach
    void setUp() {
        // El catalogo va REAL, no mockeado: la regla del margen es justo lo que
        // este test tiene que ejercer.
        PrecioCatalogoService precioCatalogo = new PrecioCatalogoService(precioRepository);
        ReflectionTestUtils.setField(precioCatalogo, "margenMaximo", new BigDecimal("30"));
        resolver = new CatalogoOcrResolver(proveedorRepository, precioCatalogo);

        ypf = new Proveedor();
        ypf.setId(1);
        ypf.setNombre("YPF En Ruta");
        ypf.setCuit("30123456789");
        ypf.setServicio(Servicio.COMBUSTIBLE);

        vigente = new Precio();
        vigente.setId(10);
        vigente.setPrecioUnitario(PRECIO_VIGENTE);
        vigente.setTipoCombustible(TipoCombustible.GASOIL_GRADO_2);
        vigente.setServicio(Servicio.COMBUSTIBLE);
        vigente.setProveedor(ypf);
        vigente.setFechaDesde(LocalDate.now().minusMonths(1));

        when(precioRepository.save(any(Precio.class))).thenAnswer(inv -> {
            Precio p = inv.getArgument(0);
            p.setId(99);
            return p;
        });
        when(proveedorRepository.save(any(Proveedor.class))).thenAnswer(inv -> {
            Proveedor p = inv.getArgument(0);
            p.setId(50);
            return p;
        });
    }

    // ------------------------------------------------------------------
    // El freno a las alucinaciones del OCR — lo más importante del archivo
    // ------------------------------------------------------------------

    /**
     * EL test. Un precio absurdamente lejos del vigente se DESCARTA y se
     * devuelve el vigente, sin tocar el catálogo.
     *
     * Y no se lanza excepción a propósito: esto alimenta una sugerencia de
     * formulario, así que trabar el análisis por un precio dudoso dejaría al
     * empleado sin poder cargar el ticket. Se degrada, no se rompe.
     */
    @Test
    void precioAlucinado_devuelveElVigenteYNoPisaElCatalogo() {
        conVigente();
        // 20.860 en vez de 2.086: el error de coma clásico del OCR.
        TicketAnalysisResponse res = resolver.resolver(ocr("YPF En Ruta", 20860.0));

        assertThat(res.precioUnitario()).isEqualByComparingTo(PRECIO_VIGENTE);
        assertThat(res.idPrecio()).isEqualTo(10);
        verify(precioRepository, never()).save(any());
    }

    @Test
    void precioDentroDelMargen_reemplazaElVigente() {
        conVigente();
        // 2.100 está a 14 del vigente (2.086): dentro del margen de 30.
        TicketAnalysisResponse res = resolver.resolver(ocr("YPF En Ruta", 2100.0));

        assertThat(res.precioUnitario()).isEqualByComparingTo("2100.0");
        // El anterior se cierra en vez de borrarse: los tickets viejos siguen
        // apuntando al precio que realmente se pagó.
        assertThat(vigente.getFechaHasta()).isNotNull();
        verify(precioRepository).save(any(Precio.class));
    }

    @Test
    void precioIgualAlVigente_loReutilizaSinCrearFilaNueva() {
        conVigente();

        TicketAnalysisResponse res = resolver.resolver(ocr("YPF En Ruta", 2086.0));

        assertThat(res.idPrecio()).isEqualTo(10);
        verify(precioRepository, never()).save(any());
    }

    /** compareTo ignora la escala: 2086 y 2086.00 son el mismo precio. */
    @Test
    void precioIgualPeroConOtraEscala_loReutiliza() {
        vigente.setPrecioUnitario(new BigDecimal("2086"));
        conVigente();

        resolver.resolver(ocr("YPF En Ruta", 2086.00));

        verify(precioRepository, never()).save(any());
    }

    /**
     * Sin vigente previo NO hay contra qué comparar el margen, así que el precio
     * leído se acepta: es el único dato que hay. El margen protege de una
     * desviación, no de un catálogo vacío.
     */
    @Test
    void sinPrecioVigentePrevio_aceptaElLeidoAunqueSeaAlto() {
        when(precioRepository.findByProveedorAndTipoCombustibleAndFechaHastaIsNull(any(), any()))
            .thenReturn(Optional.empty());
        when(proveedorRepository.findByServicio(Servicio.COMBUSTIBLE)).thenReturn(List.of(ypf));

        TicketAnalysisResponse res = resolver.resolver(ocr("YPF En Ruta", 99999.0));

        assertThat(res.precioUnitario()).isEqualByComparingTo("99999.0");
    }

    // ------------------------------------------------------------------
    // Resolución del proveedor
    // ------------------------------------------------------------------

    @Test
    void estacionQueCoincideConUnProveedor_loUsa() {
        conVigente();

        TicketAnalysisResponse res = resolver.resolver(ocr("YPF En Ruta", 2086.0));

        assertThat(res.idProveedor()).isEqualTo(1);
        assertThat(res.proveedorNombre()).isEqualTo("YPF En Ruta");
        verify(proveedorRepository, never()).save(any());
    }

    /** El match no distingue mayúsculas y admite contención en cualquier sentido. */
    @Test
    void elMatchDeNombreIgnoraMayusculas() {
        conVigente();

        TicketAnalysisResponse res = resolver.resolver(ocr("ypf en ruta", 2086.0));

        assertThat(res.idProveedor()).isEqualTo(1);
    }

    /**
     * Con VARIOS candidatos no se elige ninguno. Adivinar mal significaría
     * cargarle la compra a la estación equivocada y ensuciar su precio vigente:
     * es mejor dejarlo vacío para que el empleado lo elija.
     */
    @Test
    void variosProveedoresCandidatos_noEligeNinguno() {
        Proveedor otroYpf = new Proveedor();
        otroYpf.setId(2);
        otroYpf.setNombre("YPF");
        otroYpf.setServicio(Servicio.COMBUSTIBLE);
        when(proveedorRepository.findByServicio(Servicio.COMBUSTIBLE))
            .thenReturn(List.of(ypf, otroYpf));

        TicketAnalysisResponse res = resolver.resolver(ocr("YPF En Ruta", 2086.0));

        assertThat(res.idProveedor()).isNull();
        assertThat(res.idPrecio())
            .as("sin proveedor no hay a quien asociarle el precio")
            .isNull();
    }

    @Test
    void estacionDesconocidaConCuit_daDeAltaElProveedor() {
        when(proveedorRepository.findByServicio(Servicio.COMBUSTIBLE)).thenReturn(List.of());
        when(proveedorRepository.findByCuit("30999888777")).thenReturn(Optional.empty());
        when(precioRepository.findByProveedorAndTipoCombustibleAndFechaHastaIsNull(any(), any()))
            .thenReturn(Optional.empty());

        TicketAnalysisResponse res = resolver.resolver(
            ocrConCuit("Shell Ruta 9", "30999888777", 2100.0));

        assertThat(res.idProveedor()).isEqualTo(50);
        assertThat(res.proveedorNombre()).isEqualTo("Shell Ruta 9");
        verify(proveedorRepository).save(any(Proveedor.class));
    }

    /**
     * Sin CUIT no se puede crear el proveedor: la columna es NOT NULL. Se deja
     * en blanco para carga manual en vez de inventar un valor.
     */
    @Test
    void estacionDesconocidaSinCuit_noCreaNadaYDejaVacio() {
        when(proveedorRepository.findByServicio(Servicio.COMBUSTIBLE)).thenReturn(List.of());

        TicketAnalysisResponse res = resolver.resolver(ocr("Estación Nueva", 2100.0));

        assertThat(res.idProveedor()).isNull();
        verify(proveedorRepository, never()).save(any());
    }

    /**
     * Idempotencia: analizar dos veces la misma foto no puede dejar dos
     * proveedores con el mismo CUIT.
     */
    @Test
    void estacionDesconocidaConCuitYaExistente_reutilizaEnVezDeDuplicar() {
        when(proveedorRepository.findByServicio(Servicio.COMBUSTIBLE)).thenReturn(List.of());
        when(proveedorRepository.findByCuit("30123456789")).thenReturn(Optional.of(ypf));
        conVigenteSinMatchDeNombre();

        TicketAnalysisResponse res = resolver.resolver(
            ocrConCuit("Y.P.F. RUTA", "30123456789", 2086.0));

        assertThat(res.idProveedor()).isEqualTo(1);
        verify(proveedorRepository, never()).save(any());
    }

    @Test
    void sinEstacionLeida_noResuelveNada() {
        TicketAnalysisResponse res = resolver.resolver(ocr(null, 2086.0));

        assertThat(res.idProveedor()).isNull();
        assertThat(res.idPrecio()).isNull();
        verify(proveedorRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // Campos que el OCR no logró leer
    // ------------------------------------------------------------------

    /** Lo que el OCR no leyó queda null: el empleado lo completa a mano. */
    @Test
    void sinCombustibleLeido_dejaElPrecioVacioPeroResuelveElProveedor() {
        when(proveedorRepository.findByServicio(Servicio.COMBUSTIBLE)).thenReturn(List.of(ypf));

        TicketAnalysisResponse res = resolver.resolver(new TicketAnalysisResult(
            new BigDecimal("50.0"), LocalDateTime.now(), 104300.0, 2086.0, "YPF En Ruta", null, null));

        assertThat(res.idProveedor()).isEqualTo(1);
        assertThat(res.idPrecio()).isNull();
    }

    @Test
    void sinPrecioLeido_dejaElPrecioVacio() {
        when(proveedorRepository.findByServicio(Servicio.COMBUSTIBLE)).thenReturn(List.of(ypf));

        TicketAnalysisResponse res = resolver.resolver(new TicketAnalysisResult(
            new BigDecimal("50.0"), LocalDateTime.now(), null, null, "YPF En Ruta", null,
            TipoCombustible.GASOIL_GRADO_2));

        assertThat(res.idPrecio()).isNull();
    }

    /** Los datos crudos del ticket viajan tal cual, sin tocarse. */
    @Test
    void losDatosCrudosDelOcrViajanSinModificar() {
        conVigente();
        LocalDateTime fecha = LocalDateTime.of(2026, 8, 14, 10, 30);

        TicketAnalysisResponse res = resolver.resolver(new TicketAnalysisResult(
            new BigDecimal("50.0"), fecha, 104300.0, 2086.0, "YPF En Ruta", null,
            TipoCombustible.GASOIL_GRADO_2));

        // isEqualByComparingTo y no isEqualTo: en BigDecimal, isEqualTo exige que
        // coincida la escala ademas del valor. Lo que interesa aca es que el dato
        // del OCR viaje SIN MODIFICAR, no con que escala se lo represente.
        assertThat(res.litros()).isEqualByComparingTo("50.0");
        assertThat(res.fechaCarga()).isEqualTo(fecha);
        assertThat(res.importeTotal()).isEqualTo(104300.0);
        assertThat(res.estacion()).isEqualTo("YPF En Ruta");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void conVigente() {
        when(proveedorRepository.findByServicio(Servicio.COMBUSTIBLE)).thenReturn(List.of(ypf));
        when(precioRepository.findByProveedorAndTipoCombustibleAndFechaHastaIsNull(
            ypf, TipoCombustible.GASOIL_GRADO_2)).thenReturn(Optional.of(vigente));
    }

    private void conVigenteSinMatchDeNombre() {
        when(precioRepository.findByProveedorAndTipoCombustibleAndFechaHastaIsNull(
            ypf, TipoCombustible.GASOIL_GRADO_2)).thenReturn(Optional.of(vigente));
    }

    private TicketAnalysisResult ocr(String estacion, double precioPorLitro) {
        return new TicketAnalysisResult(
            new BigDecimal("50.0"), LocalDateTime.now(), 104300.0, precioPorLitro, estacion, null,
            TipoCombustible.GASOIL_GRADO_2);
    }

    private TicketAnalysisResult ocrConCuit(String estacion, String cuit, double precioPorLitro) {
        return new TicketAnalysisResult(
            new BigDecimal("50.0"), LocalDateTime.now(), 104300.0, precioPorLitro, estacion, cuit,
            TipoCombustible.GASOIL_GRADO_2);
    }
}
