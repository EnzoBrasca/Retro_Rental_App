package com.retrorental.backend.config;

import com.retrorental.backend.model.Precio;
import com.retrorental.backend.model.Proveedor;
import com.retrorental.backend.model.enums.Servicio;
import com.retrorental.backend.model.enums.TipoCombustible;
import com.retrorental.backend.repository.PrecioRepository;
import com.retrorental.backend.repository.ProveedorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Carga datos semilla en desarrollo (@Profile("dev")).
 *
 * Qué siembra: las estaciones de servicio (proveedores) y los precios de
 * combustible vigentes — el catálogo mínimo que el formulario de carga de
 * tickets necesita para funcionar.
 *
 * Qué NO siembra: vehículos. El parque se carga desde el ABM del administrador
 * con las patentes y datos reales de la empresa.
 *
 * Es idempotente: cada tabla se siembra solo si está vacía, así reiniciar la
 * app no genera duplicados.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final ProveedorRepository proveedorRepository;
    private final PrecioRepository precioRepository;

    @Override
    public void run(String... args) {
        seedProveedores();
        seedPrecios();
    }

    private void seedProveedores() {
        if (proveedorRepository.count() > 0) {
            log.info("[seed] proveedores ya existen, se omite");
            return;
        }
        // Estaciones de servicio reales de Argentina. Los CUIT son representativos
        // para datos de desarrollo (el de YPF S.A. es el real).
        List<Proveedor> proveedores = List.of(
            proveedor("YPF", "30-54668997-9"),
            proveedor("Shell (Raízen)", "30-50000397-6"),
            proveedor("Axion Energy", "30-71025413-2"),
            proveedor("Puma Energy", "30-70858199-8"),
            proveedor("Gulf", "30-71104553-0")
        );
        proveedorRepository.saveAll(proveedores);
        log.info("[seed] {} proveedores creados", proveedores.size());
    }

    // Precio base de referencia por producto (CABA, julio 2026) por litro, salvo
    // GNC (por m³). Cada estación aplica una variación (ver FACTOR_POR_PROVEEDOR)
    // para reflejar que el mismo combustible cuesta distinto según el proveedor.
    private static final Map<TipoCombustible, BigDecimal> PRECIO_BASE = Map.of(
        TipoCombustible.NAFTA_SUPER, new BigDecimal("2054.00"),
        TipoCombustible.NAFTA_PREMIUM, new BigDecimal("2380.00"),
        TipoCombustible.GASOIL_GRADO_2, new BigDecimal("2180.00"),
        TipoCombustible.GASOIL_GRADO_3, new BigDecimal("2480.00"),
        TipoCombustible.GNC, new BigDecimal("780.00")
    );

    // Factor de precio por estación (clave = nombre del proveedor sembrado).
    private static final Map<String, BigDecimal> FACTOR_POR_PROVEEDOR = Map.of(
        "YPF", new BigDecimal("1.00"),
        "Shell (Raízen)", new BigDecimal("1.03"),
        "Axion Energy", new BigDecimal("1.02"),
        "Puma Energy", new BigDecimal("0.99"),
        "Gulf", new BigDecimal("0.98")
    );

    // Garantiza un precio vigente por cada (proveedor, combustible): asume que toda
    // estación vende todos los productos. El precio nace del base × factor de la
    // estación, redondeado a 2 decimales.
    //
    // Es SELF-HEALING, no idempotente-por-count. No se puede gatear con "la tabla
    // ya tiene precios y me voy": con ddl-auto=update la data persiste entre
    // reinicios, así que una DB sembrada ANTES de que Precio tuviera `proveedor`
    // quedó con TODOS los precios en proveedor_id NULL. Como el front elige el
    // precio por (proveedor + combustible), esos legacy no matchean nunca y el
    // precio no aparece al elegir proveedor. Acá se rellenan los (proveedor,
    // combustible) que falten y se retiran los vigentes de combustible sin
    // proveedor, dejando el catálogo consistente con el modelo por estación.
    private void seedPrecios() {
        LocalDate hoy = LocalDate.now();
        List<Proveedor> proveedores = proveedorRepository.findAll();

        int creados = 0;
        for (Proveedor prov : proveedores) {
            BigDecimal factor = FACTOR_POR_PROVEEDOR.getOrDefault(prov.getNombre(), BigDecimal.ONE);
            for (TipoCombustible tc : TipoCombustible.values()) {
                // Ya hay un vigente para esta estación y producto: no se toca (puede
                // haber sido actualizado por un ticket, invariante: un solo vigente).
                boolean yaVigente = precioRepository
                    .findByProveedorAndTipoCombustibleAndFechaHastaIsNull(prov, tc)
                    .isPresent();
                if (yaVigente) {
                    continue;
                }
                BigDecimal valor = PRECIO_BASE.get(tc).multiply(factor).setScale(2, RoundingMode.HALF_UP);
                precioRepository.save(precio(prov, tc, valor, hoy));
                creados++;
            }
        }

        // Retira los precios de combustible legacy sin proveedor: el front ya no los
        // puede elegir (elige por proveedor) y ensucian el catálogo vigente. Se
        // cierran con fechaHasta (no se borran) para no romper tickets históricos
        // que los referencian por id.
        List<Precio> legacySinProveedor = precioRepository.findByServicio(Servicio.COMBUSTIBLE).stream()
            .filter(p -> p.getFechaHasta() == null && p.getProveedor() == null)
            .toList();
        legacySinProveedor.forEach(p -> p.setFechaHasta(hoy));
        precioRepository.saveAll(legacySinProveedor);

        if (creados > 0 || !legacySinProveedor.isEmpty()) {
            log.info("[seed] precios reparados: {} creados por (estacion x producto), {} legacy sin proveedor retirados",
                creados, legacySinProveedor.size());
        } else {
            log.info("[seed] precios ya consistentes, no se aplicaron cambios");
        }
    }

    private Proveedor proveedor(String nombre, String cuit) {
        Proveedor p = new Proveedor();
        p.setNombre(nombre);
        p.setCuit(cuit);
        p.setServicio(Servicio.COMBUSTIBLE);
        return p;
    }

    private Precio precio(Proveedor proveedor, TipoCombustible tipoCombustible,
            BigDecimal precioUnitario, LocalDate desde) {
        Precio p = new Precio();
        p.setProveedor(proveedor);
        p.setTipoCombustible(tipoCombustible);
        p.setPrecioUnitario(precioUnitario);
        p.setServicio(Servicio.COMBUSTIBLE);
        p.setFechaDesde(desde);
        p.setFechaHasta(null); // vigente
        return p;
    }
}
