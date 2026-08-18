package com.retrorental.backend.service;

import com.retrorental.backend.dto.request.CreateHabilitadoRequest;
import com.retrorental.backend.dto.response.HabilitadoResponse;
import com.retrorental.backend.exception.ConflictException;
import com.retrorental.backend.exception.ErrorCode;
import com.retrorental.backend.exception.ForbiddenException;
import com.retrorental.backend.exception.ResourceNotFoundException;
import com.retrorental.backend.model.EmpleadoHabilitado;
import com.retrorental.backend.model.Persona;
import com.retrorental.backend.repository.EmpleadoHabilitadoRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Padron de documentos autorizados a registrarse por /auth/register.
 *
 * El registro publico se conserva (el empleado se da de alta solo, con el mismo
 * formulario de siempre); lo que cambia es que deja de estar abierto al mundo.
 * El administrador carga los documentos de su nomina —que ya tiene— y solo esa
 * gente puede crear una cuenta.
 */
@Service
@RequiredArgsConstructor
public class HabilitadoService {

    private static final Logger log = LoggerFactory.getLogger(HabilitadoService.class);

    /**
     * UNICO mensaje de rechazo del registro publico. Lo comparten las tres
     * formas de rechazo del padron (documento fuera del padron, apellido que no
     * coincide, habilitacion ya consumida) y tambien el rechazo por documento
     * ya registrado que aplica AuthService.
     *
     * Es deliberado: un mensaje distinto por caso convierte al endpoint en un
     * oraculo con el que un desconocido puede averiguar que documentos
     * pertenecen al personal del cliente, o cuales ya tienen cuenta, probando
     * numeros. El registro publico tiene una sola respuesta de fracaso.
     *
     * El texto cubre las dos salidas reales que tiene el empleado, porque desde
     * afuera no se le puede decir en cual esta sin filtrar el dato.
     */
    public static final String RECHAZO_REGISTRO =
        "No podés registrarte con esos datos. Si ya tenés cuenta, iniciá sesión. "
        + "Si no, pedile a tu jefe que te habilite en el sistema.";

    private final EmpleadoHabilitadoRepository habilitadoRepository;

    // -----------------------------------------------------------------------
    // Consumo desde el registro publico
    // -----------------------------------------------------------------------

    /**
     * Verifica que el documento este habilitado y que el apellido coincida.
     * Devuelve la habilitacion para que el llamador la consuma con
     * {@link #marcarUsado} DESPUES de crear la persona.
     *
     * No marca nada: separar validar de consumir permite que el registro aborte
     * (por documento duplicado, por ejemplo) sin haber quemado la habilitacion.
     */
    @Transactional(readOnly = true)
    public EmpleadoHabilitado validarHabilitacion(String documento, String apellido) {
        Optional<EmpleadoHabilitado> encontrado = habilitadoRepository.findByDocumento(documento);

        if (encontrado.isEmpty()) {
            // Se loguea el intento porque una racha de estos es exactamente la
            // pinta que tiene alguien probando documentos contra el padron.
            log.warn("Registro rechazado: documento fuera del padron");
            throw new ForbiddenException(ErrorCode.REGISTRO_NO_HABILITADO, RECHAZO_REGISTRO);
        }

        EmpleadoHabilitado habilitado = encontrado.get();

        if (habilitado.estaUsada()) {
            log.warn("Registro rechazado: la habilitacion del documento ya fue consumida");
            throw new ForbiddenException(ErrorCode.REGISTRO_NO_HABILITADO, RECHAZO_REGISTRO);
        }

        if (!coincideApellido(habilitado.getApellido(), apellido)) {
            log.warn("Registro rechazado: el apellido no coincide con el padron");
            throw new ForbiddenException(ErrorCode.REGISTRO_NO_HABILITADO, RECHAZO_REGISTRO);
        }

        return habilitado;
    }

    /**
     * Consume la habilitacion. Se llama con la persona ya persistida, dentro de
     * la MISMA transaccion que la creo: si algo falla despues, no queda ni la
     * cuenta ni la habilitacion quemada.
     */
    @Transactional
    public void marcarUsado(EmpleadoHabilitado habilitado, Persona persona) {
        habilitado.setFechaUso(LocalDateTime.now());
        habilitado.setPersona(persona);
        habilitadoRepository.save(habilitado);
    }

    /**
     * Consume la habilitacion de un documento si existe y esta libre. La usa el
     * alta manual del administrador (POST /admin/empleados), que no pasa por el
     * padron: sin esto, la fila quedaria figurando como "sin registrar" para
     * siempre aunque la cuenta ya exista.
     */
    @Transactional
    public void marcarUsadoSiExiste(String documento, Persona persona) {
        habilitadoRepository.findByDocumento(documento)
            .filter(h -> !h.estaUsada())
            .ifPresent(h -> marcarUsado(h, persona));
    }

    // -----------------------------------------------------------------------
    // ABM del administrador
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<HabilitadoResponse> listAll() {
        return habilitadoRepository.findAllConPersona().stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public HabilitadoResponse create(CreateHabilitadoRequest request) {
        if (habilitadoRepository.existsByDocumento(request.getDocumento())) {
            throw new ConflictException(ErrorCode.HABILITADO_ALREADY_EXISTS,
                "Ese documento ya está en el padrón", "documento");
        }

        EmpleadoHabilitado habilitado = new EmpleadoHabilitado();
        habilitado.setDocumento(request.getDocumento());
        habilitado.setApellido(request.getApellido());
        habilitado.setNombre(request.getNombre());
        habilitado.setFechaAlta(LocalDate.now());

        return toResponse(habilitadoRepository.save(habilitado));
    }

    /**
     * Alta masiva, para cargar la nomina de una sola vez. Los documentos que ya
     * estaban en el padron se SALTEAN en lugar de abortar todo: cargar una lista
     * de cincuenta y que falle entera por un repetido no le sirve a nadie.
     * Devuelve el padron completo ya actualizado.
     */
    @Transactional
    public List<HabilitadoResponse> createBulk(List<CreateHabilitadoRequest> requests) {
        for (CreateHabilitadoRequest request : requests) {
            if (!habilitadoRepository.existsByDocumento(request.getDocumento())) {
                EmpleadoHabilitado habilitado = new EmpleadoHabilitado();
                habilitado.setDocumento(request.getDocumento());
                habilitado.setApellido(request.getApellido());
                habilitado.setNombre(request.getNombre());
                habilitado.setFechaAlta(LocalDate.now());
                habilitadoRepository.save(habilitado);
            }
        }
        return listAll();
    }

    /**
     * Quita un documento del padron. Solo si NO se uso: una vez que la cuenta
     * existe, borrar la fila del padron no le saca el acceso a nadie (el token
     * y la password siguen funcionando) y ademas destruye la trazabilidad de
     * quien autorizo esa alta. Para sacarle el acceso a alguien que ya se
     * registro esta la baja de empleado.
     */
    @Transactional
    public void delete(Integer id) {
        EmpleadoHabilitado habilitado = habilitadoRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.HABILITADO_NOT_FOUND, "Esa habilitación no existe"));

        if (habilitado.estaUsada()) {
            throw new ConflictException(ErrorCode.HABILITADO_ALREADY_USED,
                "Ese empleado ya se registró. Para quitarle el acceso, dalo de baja desde Personal");
        }

        habilitadoRepository.delete(habilitado);
    }

    // -----------------------------------------------------------------------
    // Interno
    // -----------------------------------------------------------------------

    /**
     * Compara apellidos de forma tolerante a como los escriba cada uno: sin
     * distinguir mayusculas, sin acentos y sin espacios de mas.
     *
     * "PEREZ", "Pérez" y " pérez " son la misma persona. Ser estricto aca no
     * agrega seguridad —el atacante que conoce el documento tambien conoce el
     * apellido— y en cambio traba al empleado real, que es a quien no queremos
     * trabar.
     */
    private boolean coincideApellido(String delPadron, String delRegistro) {
        return normalizar(delPadron).equals(normalizar(delRegistro));
    }

    private String normalizar(String valor) {
        if (valor == null) {
            return "";
        }
        // NFD separa la letra de su acento; el replaceAll borra los acentos
        // sueltos y deja la letra base ("é" -> "e").
        String sinAcentos = Normalizer.normalize(valor, Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return sinAcentos.trim().replaceAll("\\s+", " ").toUpperCase();
    }

    private HabilitadoResponse toResponse(EmpleadoHabilitado habilitado) {
        Persona persona = habilitado.getPersona();
        return new HabilitadoResponse(
            habilitado.getId(),
            habilitado.getDocumento(),
            habilitado.getApellido(),
            habilitado.getNombre(),
            habilitado.getFechaAlta(),
            habilitado.estaUsada(),
            habilitado.getFechaUso(),
            persona != null ? persona.getUsername() : null
        );
    }
}
