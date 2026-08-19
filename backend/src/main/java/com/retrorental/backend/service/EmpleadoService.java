package com.retrorental.backend.service;

import com.retrorental.backend.dto.request.CreateEmpleadoRequest;
import com.retrorental.backend.dto.request.UpdateEmpleadoRequest;
import com.retrorental.backend.dto.response.EmpleadoResponse;
import com.retrorental.backend.dto.response.TelefonoResponse;
import com.retrorental.backend.exception.ConflictException;
import com.retrorental.backend.exception.ErrorCode;
import com.retrorental.backend.exception.ResourceNotFoundException;
import com.retrorental.backend.model.Empleado;
import com.retrorental.backend.model.Persona;
import com.retrorental.backend.model.Vehiculo;
import com.retrorental.backend.model.embeddable.Telefono;
import com.retrorental.backend.model.enums.Rol;
import com.retrorental.backend.repository.PersonaRepository;
import com.retrorental.backend.repository.VehiculoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmpleadoService {

    private final PersonaRepository personaRepository;
    private final VehiculoRepository vehiculoRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsernameGenerator usernameGenerator;
    private final HabilitadoService habilitadoService;

    /** Listado completo del personal, incluidos los dados de baja (para gestión). */
    @Transactional(readOnly = true)
    public List<EmpleadoResponse> listAll() {
        return personaRepository.findAllByOrderByFechaAltaDesc().stream()
            .map(this::toResponse)
            .toList();
    }

    /**
     * Alta de un empleado (solo admin). El documento es único; si ya existe se
     * rechaza con 409. El username se autogenera a partir de nombre+apellido.
     */
    @Transactional
    public EmpleadoResponse create(CreateEmpleadoRequest request) {
        if (personaRepository.existsByDocumento(request.getDocumento())) {
            throw new ConflictException(
                ErrorCode.DOCUMENTO_ALREADY_EXISTS, "Ya existe un usuario con ese documento", "documento");
        }

        Empleado empleado = new Empleado();
        empleado.setFechaAlta(LocalDate.now());
        empleado.setRol(Rol.EMPLEADO);
        empleado.setNombre(request.getNombre());
        empleado.setApellido(request.getApellido());
        empleado.setDocumento(request.getDocumento());
        empleado.setPassword(passwordEncoder.encode(request.getPassword()));

        String username = usernameGenerator.generate(request.getNombre(), request.getApellido());
        empleado.setUsername(username);

        Telefono telefono = new Telefono();
        telefono.setCodigoArea(request.getTelefono().getCodigoArea());
        telefono.setTelefono(request.getTelefono().getNumero());
        empleado.setTelefono(telefono);

        Empleado creado = personaRepository.save(empleado);

        // El alta manual no pasa por el padron, pero si ese documento estaba
        // habilitado hay que consumir la fila igual: de lo contrario le queda
        // al jefe figurando como "sin registrar" un empleado que el mismo creo.
        habilitadoService.marcarUsadoSiExiste(creado.getDocumento(), creado);

        return toResponse(creado);
    }

    /**
     * Edición completa de un empleado (solo admin). El username no se edita
     * nunca: es inmutable desde el alta (igual criterio que el documento).
     */
    @Transactional
    public EmpleadoResponse update(Integer id, UpdateEmpleadoRequest request) {
        Empleado empleado = resolveEmpleado(id);

        empleado.setNombre(request.getNombre());
        empleado.setApellido(request.getApellido());

        Telefono telefono = new Telefono();
        telefono.setCodigoArea(request.getTelefono().getCodigoArea());
        telefono.setTelefono(request.getTelefono().getNumero());
        empleado.setTelefono(telefono);

        return toResponse(personaRepository.save(empleado));
    }

    /**
     * Baja lógica de un empleado (solo admin). No borra la fila: setea
     * fechaBaja. Además libera los vehiculos que tenía asignados, para que
     * queden disponibles para otro operario. Idempotencia: si ya estaba dado
     * de baja, se rechaza con 409.
     */
    @Transactional
    public void desactivar(Integer id) {
        Empleado empleado = resolveEmpleado(id);
        if (empleado.getFechaBaja() != null) {
            throw new ConflictException(
                ErrorCode.EMPLEADO_ALREADY_INACTIVE, "El empleado ya está dado de baja");
        }

        // UNA sentencia en vez de un UPDATE por vehiculo (ver
        // docs/BACKEND-AUDIT.md, DB-08). La query de modificacion pasa por
        // encima del contexto de persistencia, asi que el repositorio la declara
        // con clearAutomatically para que nadie se quede con un Vehiculo cargado
        // mostrando el operario viejo.
        vehiculoRepository.desasignarTodosDe(empleado.getId());

        empleado.setFechaBaja(LocalDate.now());
        personaRepository.save(empleado);
    }

    private Empleado resolveEmpleado(Integer id) {
        Persona persona = personaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.EMPLEADO_NOT_FOUND, "Empleado no encontrado"));
        if (!(persona instanceof Empleado empleado)) {
            throw new ResourceNotFoundException(
                ErrorCode.EMPLEADO_NOT_FOUND, "Empleado no encontrado");
        }
        return empleado;
    }

    private EmpleadoResponse toResponse(Empleado empleado) {
        Telefono tel = empleado.getTelefono();
        TelefonoResponse telefono = tel != null
            ? new TelefonoResponse(tel.getCodigoArea(), tel.getTelefono())
            : null;

        return new EmpleadoResponse(
            empleado.getId(),
            empleado.getNombre(),
            empleado.getApellido(),
            empleado.getDocumento(),
            empleado.getUsername(),
            telefono,
            empleado.getFechaAlta(),
            empleado.getFechaBaja()
        );
    }
}
