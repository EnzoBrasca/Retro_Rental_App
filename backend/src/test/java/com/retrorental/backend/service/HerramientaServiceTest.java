package com.retrorental.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.retrorental.backend.dto.request.CreateHerramientaRequest;
import com.retrorental.backend.dto.request.UpdateHerramientaRequest;
import com.retrorental.backend.dto.response.HerramientaResponse;
import com.retrorental.backend.exception.ConflictException;
import com.retrorental.backend.exception.ErrorCode;
import com.retrorental.backend.exception.ResourceNotFoundException;
import com.retrorental.backend.model.Herramienta;
import com.retrorental.backend.repository.HerramientaRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@Tag("herramienta")
class HerramientaServiceTest {

    @Mock private HerramientaRepository herramientaRepository;

    @InjectMocks private HerramientaService service;

    private Herramienta herramienta() {
        Herramienta h = new Herramienta();
        h.setId(1);
        h.setNombre("Motosierra Stihl");
        h.setCapacidad(new BigDecimal("0.30"));
        return h;
    }

    @Test
    void listActivas_devuelveSoloLasSinFechaBaja() {
        when(herramientaRepository.findByFechaBajaIsNull()).thenReturn(List.of(herramienta()));

        List<HerramientaResponse> resultado = service.listActivas();

        assertEquals(1, resultado.size());
        assertEquals("Motosierra Stihl", resultado.get(0).nombre());
    }

    @Test
    void listAll_incluyeLasDadasDeBaja() {
        Herramienta baja = herramienta();
        baja.setFechaBaja(LocalDate.now());
        when(herramientaRepository.findAll()).thenReturn(List.of(herramienta(), baja));

        List<HerramientaResponse> resultado = service.listAll();

        assertEquals(2, resultado.size());
    }

    @Test
    void create_conDatosValidos_persisteNombreYCapacidad() {
        CreateHerramientaRequest req = new CreateHerramientaRequest();
        req.setNombre(" Bidon 20L ");
        req.setCapacidad(new BigDecimal("20.00"));
        when(herramientaRepository.save(any(Herramienta.class))).thenAnswer(i -> {
            Herramienta h = i.getArgument(0);
            h.setId(2);
            return h;
        });

        HerramientaResponse resp = service.create(req);

        assertEquals(2, resp.id());
        // Se recorta el espacio de los bordes, mismo criterio que Vehiculo.
        assertEquals("Bidon 20L", resp.nombre());
        assertEquals(new BigDecimal("20.00"), resp.capacidad());
    }

    @Test
    void update_herramientaInexistente_rechaza() {
        when(herramientaRepository.findById(99)).thenReturn(Optional.empty());
        UpdateHerramientaRequest req = new UpdateHerramientaRequest();
        req.setNombre("X");
        req.setCapacidad(BigDecimal.ONE);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
            () -> service.update(99, req));

        assertEquals(ErrorCode.HERRAMIENTA_NOT_FOUND, ex.getCode());
    }

    @Test
    void update_conDatosValidos_actualizaLosCampos() {
        Herramienta existente = herramienta();
        when(herramientaRepository.findById(1)).thenReturn(Optional.of(existente));
        when(herramientaRepository.save(any(Herramienta.class))).thenAnswer(i -> i.getArgument(0));

        UpdateHerramientaRequest req = new UpdateHerramientaRequest();
        req.setNombre("Motosierra Husqvarna");
        req.setCapacidad(new BigDecimal("0.40"));

        HerramientaResponse resp = service.update(1, req);

        assertEquals("Motosierra Husqvarna", resp.nombre());
        assertEquals(new BigDecimal("0.40"), resp.capacidad());
    }

    // ------------------------------------------------- relacion de mezcla

    @Test
    void create_conRelacionDeMezcla_laPersiste() {
        CreateHerramientaRequest req = new CreateHerramientaRequest();
        req.setNombre("Motosierra vieja");
        req.setCapacidad(new BigDecimal("0.30"));
        req.setRelacionMezcla(25);
        when(herramientaRepository.save(any(Herramienta.class))).thenAnswer(i -> i.getArgument(0));

        HerramientaResponse resp = service.create(req);

        assertEquals(25, resp.relacionMezcla());
    }

    /**
     * El APK 1.7.0 sigue instalado y su ABM no manda relacionMezcla. Si el campo
     * fuera obligatorio, el alta de herramientas se le romperia al admin hasta
     * que actualice. Por eso es opcional y el service completa el default.
     */
    @Test
    void create_sinRelacionDeMezcla_usaElDefault() {
        CreateHerramientaRequest req = new CreateHerramientaRequest();
        req.setNombre("Bidon 20L");
        req.setCapacidad(new BigDecimal("20.00"));
        when(herramientaRepository.save(any(Herramienta.class))).thenAnswer(i -> i.getArgument(0));

        HerramientaResponse resp = service.create(req);

        assertEquals(Herramienta.RELACION_MEZCLA_DEFAULT, resp.relacionMezcla());
    }

    @Test
    void update_conRelacionDeMezcla_laActualiza() {
        Herramienta existente = herramienta();
        when(herramientaRepository.findById(1)).thenReturn(Optional.of(existente));
        when(herramientaRepository.save(any(Herramienta.class))).thenAnswer(i -> i.getArgument(0));

        UpdateHerramientaRequest req = new UpdateHerramientaRequest();
        req.setNombre("Motosierra Husqvarna");
        req.setCapacidad(new BigDecimal("0.40"));
        req.setRelacionMezcla(32);

        assertEquals(32, service.update(1, req).relacionMezcla());
    }

    /**
     * A diferencia del resto de los campos, una relacion ausente NO pisa la que
     * ya estaba. El PUT es reemplazo completo, pero un cliente viejo que omite el
     * campo estaria borrando un dato que el admin cargo a mano, y encima en
     * silencio. Conservarla es lo unico que no pierde informacion.
     */
    @Test
    void update_sinRelacionDeMezcla_conservaLaQueYaTenia() {
        Herramienta existente = herramienta();
        existente.setRelacionMezcla(25);
        when(herramientaRepository.findById(1)).thenReturn(Optional.of(existente));
        when(herramientaRepository.save(any(Herramienta.class))).thenAnswer(i -> i.getArgument(0));

        UpdateHerramientaRequest req = new UpdateHerramientaRequest();
        req.setNombre("Motosierra Husqvarna");
        req.setCapacidad(new BigDecimal("0.40"));

        assertEquals(25, service.update(1, req).relacionMezcla());
    }

    @Test
    void update_deUnaHerramientaSinRelacion_completaElDefault() {
        Herramienta existente = herramienta();
        existente.setRelacionMezcla(null);
        when(herramientaRepository.findById(1)).thenReturn(Optional.of(existente));
        when(herramientaRepository.save(any(Herramienta.class))).thenAnswer(i -> i.getArgument(0));

        UpdateHerramientaRequest req = new UpdateHerramientaRequest();
        req.setNombre("Motosierra Husqvarna");
        req.setCapacidad(new BigDecimal("0.40"));

        assertEquals(Herramienta.RELACION_MEZCLA_DEFAULT, service.update(1, req).relacionMezcla());
    }

    @Test
    void desactivar_marcaFechaBaja() {
        Herramienta existente = herramienta();
        when(herramientaRepository.findById(1)).thenReturn(Optional.of(existente));

        service.desactivar(1);

        assertNotNull(existente.getFechaBaja());
    }

    @Test
    void desactivar_yaDadaDeBaja_rechaza() {
        Herramienta baja = herramienta();
        baja.setFechaBaja(LocalDate.now());
        when(herramientaRepository.findById(1)).thenReturn(Optional.of(baja));

        ConflictException ex = assertThrows(ConflictException.class, () -> service.desactivar(1));

        assertEquals(ErrorCode.HERRAMIENTA_ALREADY_INACTIVE, ex.getCode());
    }

    @Test
    void desactivar_inexistente_devuelve404() {
        when(herramientaRepository.findById(99)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
            () -> service.desactivar(99));

        assertEquals(ErrorCode.HERRAMIENTA_NOT_FOUND, ex.getCode());
    }
}
