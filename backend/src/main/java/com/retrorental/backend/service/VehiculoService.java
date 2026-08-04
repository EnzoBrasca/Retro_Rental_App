package com.retrorental.backend.service;

import com.retrorental.backend.dto.request.CreateVehiculoRequest;
import com.retrorental.backend.dto.request.UpdateVehiculoRequest;
import com.retrorental.backend.dto.response.VehiculoResponse;
import com.retrorental.backend.exception.ConflictException;
import com.retrorental.backend.exception.ErrorCode;
import com.retrorental.backend.exception.ForbiddenException;
import com.retrorental.backend.exception.ResourceNotFoundException;
import com.retrorental.backend.model.Empleado;
import com.retrorental.backend.model.Persona;
import com.retrorental.backend.model.Vehiculo;
import com.retrorental.backend.model.enums.Estado;
import com.retrorental.backend.repository.PersonaRepository;
import com.retrorental.backend.repository.VehiculoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VehiculoService {

    private final PersonaRepository personaRepository;
    private final VehiculoRepository vehiculoRepository;

    /**
     * Listado completo de la maquinaria del parque. No filtra por estado: el
     * estado (DISPONIBLE / EN_USO / EN_MANTENIMIENTO) viaja en cada item para
     * que el cliente lo muestre o filtre. Accesible a cualquier autenticado.
     */
    @Transactional(readOnly = true)
    public List<VehiculoResponse> listAll() {
        return vehiculoRepository.findAll().stream().map(this::toResponse).toList();
    }

    /**
     * Alta de un vehiculo (solo admin). La patente es única; si ya existe se
     * rechaza con 409. Sin "estado" en el request, se crea DISPONIBLE.
     */
    @Transactional
    public VehiculoResponse create(CreateVehiculoRequest request) {
        if (vehiculoRepository.existsByPatente(request.getPatente())) {
            throw new ConflictException(
                ErrorCode.PATENTE_ALREADY_EXISTS, "Ya existe un vehiculo con esa patente", "patente");
        }

        Vehiculo vehiculo = new Vehiculo();
        vehiculo.setPatente(request.getPatente());
        vehiculo.setTipoVehiculo(request.getTipoVehiculo());
        vehiculo.setTipoCombustible(request.getTipoCombustible());
        vehiculo.setCapacidadTanque(request.getCapacidadTanque());
        vehiculo.setEstado(request.getEstado() != null ? request.getEstado() : Estado.DISPONIBLE);
        vehiculo.setFechaUltimoMantenimiento(request.getFechaUltimoMantenimiento());
        vehiculo.setUsoAcumulado(request.getUsoAcumulado());
        vehiculo.setConsumoPromedio(request.getConsumoPromedio());

        return toResponse(vehiculoRepository.save(vehiculo));
    }

    /**
     * Edición completa de un vehiculo (solo admin). Si cambia la patente, se
     * valida que no colisione con OTRO vehiculo.
     */
    @Transactional
    public VehiculoResponse update(Integer id, UpdateVehiculoRequest request) {
        Vehiculo vehiculo = vehiculoRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.VEHICULO_NOT_FOUND, "Vehiculo no encontrado"));

        vehiculoRepository.findByPatente(request.getPatente())
            .filter(otro -> !otro.getId().equals(id))
            .ifPresent(otro -> {
                throw new ConflictException(
                    ErrorCode.PATENTE_ALREADY_EXISTS, "Ya existe otro vehiculo con esa patente", "patente");
            });

        vehiculo.setPatente(request.getPatente());
        vehiculo.setTipoVehiculo(request.getTipoVehiculo());
        vehiculo.setTipoCombustible(request.getTipoCombustible());
        vehiculo.setCapacidadTanque(request.getCapacidadTanque());
        vehiculo.setEstado(request.getEstado());
        vehiculo.setFechaUltimoMantenimiento(request.getFechaUltimoMantenimiento());
        vehiculo.setUsoAcumulado(request.getUsoAcumulado());
        vehiculo.setConsumoPromedio(request.getConsumoPromedio());

        return toResponse(vehiculoRepository.save(vehiculo));
    }

    /**
     * Baja lógica de un vehiculo (solo admin). No borra la fila porque los
     * tickets lo referencian con FK NOT NULL: setea fechaBaja. Idempotencia: si
     * ya estaba dado de baja, se rechaza con 409.
     */
    @Transactional
    public void desactivar(Integer id) {
        Vehiculo vehiculo = vehiculoRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.VEHICULO_NOT_FOUND, "Vehiculo no encontrado"));
        if (vehiculo.getFechaBaja() != null) {
            throw new ConflictException(
                ErrorCode.VEHICULO_ALREADY_INACTIVE, "El vehiculo ya está dado de baja");
        }
        // Al dar de baja liberamos la asignación: si algún empleado lo tenía
        // tomado, deja de aparecer en su flota y no puede cargarle tickets. Así
        // se evita registrar cargas sobre un vehiculo que ya no existe operativamente.
        vehiculo.setOperario(null);
        vehiculo.setFechaBaja(LocalDate.now());
        vehiculoRepository.save(vehiculo);
    }

    /**
     * Devuelve los vehiculos asignados al empleado autenticado. Es la lista que
     * la app del empleado usa para elegir qué máquina está cargando al crear un
     * ticket (solo puede cargar sobre vehiculos que tiene asignados).
     */
    @Transactional(readOnly = true)
    public List<VehiculoResponse> listAsignados(String empleadoUsername) {
        Persona persona = personaRepository.findByUsername(empleadoUsername)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.USER_NOT_FOUND, "Usuario no encontrado"));
        if (!(persona instanceof Empleado empleado)) {
            throw new ForbiddenException(
                ErrorCode.NOT_EMPLOYEE, "Solo un empleado tiene vehiculos asignados");
        }

        List<Vehiculo> vehiculos = empleado.getVehiculos();
        if (vehiculos == null) {
            return List.of();
        }
        return vehiculos.stream().map(this::toResponse).toList();
    }

    /**
     * El empleado autenticado TOMA un vehiculo libre (self-service). Solo se
     * puede tomar un vehiculo activo, DISPONIBLE y sin operario. Idempotente: si
     * ya lo tiene el mismo empleado, no falla. Al tomarlo pasa a EN_USO.
     */
    @Transactional
    public VehiculoResponse tomar(String empleadoUsername, Integer vehiculoId) {
        Empleado empleado = resolveEmpleado(empleadoUsername);
        Vehiculo vehiculo = vehiculoRepository.findById(vehiculoId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.VEHICULO_NOT_FOUND, "Vehiculo no encontrado"));

        if (vehiculo.getFechaBaja() != null) {
            throw new ConflictException(
                ErrorCode.VEHICULO_ALREADY_INACTIVE, "El vehiculo está dado de baja");
        }
        if (vehiculo.getOperario() != null) {
            // Ya lo tiene este mismo empleado → idempotente. Otro → conflicto.
            if (vehiculo.getOperario().getId().equals(empleado.getId())) {
                return toResponse(vehiculo);
            }
            throw new ConflictException(
                ErrorCode.VEHICULO_ALREADY_ASSIGNED, "El vehiculo ya está tomado por otro operario");
        }
        if (vehiculo.getEstado() != Estado.DISPONIBLE) {
            throw new ConflictException(
                ErrorCode.VEHICULO_NOT_AVAILABLE, "El vehiculo no está disponible");
        }

        vehiculo.setOperario(empleado);
        vehiculo.setEstado(Estado.EN_USO);
        return toResponse(vehiculoRepository.save(vehiculo));
    }

    /**
     * El empleado autenticado LIBERA un vehiculo que tiene asignado, para que
     * otro pueda tomarlo. Baja lógica de la asignación: no se borra nada, solo
     * se limpia el operario y el vehiculo vuelve a DISPONIBLE. Solo puede
     * liberarlo el empleado que lo tiene asignado.
     */
    @Transactional
    public void liberar(String empleadoUsername, Integer vehiculoId) {
        Empleado empleado = resolveEmpleado(empleadoUsername);
        Vehiculo vehiculo = vehiculoRepository.findById(vehiculoId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.VEHICULO_NOT_FOUND, "Vehiculo no encontrado"));

        Empleado operario = vehiculo.getOperario();
        if (operario == null || !operario.getId().equals(empleado.getId())) {
            throw new ForbiddenException(
                ErrorCode.VEHICULO_NOT_ASSIGNED, "El vehiculo no está asignado a este empleado");
        }

        vehiculo.setOperario(null);
        vehiculo.setEstado(Estado.DISPONIBLE);
        vehiculoRepository.save(vehiculo);
    }

    private VehiculoResponse toResponse(Vehiculo vehiculo) {
        Empleado operario = vehiculo.getOperario();
        return new VehiculoResponse(
            vehiculo.getId(),
            vehiculo.getPatente(),
            vehiculo.getTipoVehiculo(),
            vehiculo.getTipoCombustible(),
            vehiculo.getEstado(),
            vehiculo.getCapacidadTanque(),
            vehiculo.getUsoAcumulado(),
            vehiculo.getTipoVehiculo().unidadUso(),
            vehiculo.getConsumoPromedio(),
            vehiculo.getFechaBaja(),
            operario != null ? operario.getId() : null,
            operario != null ? operario.getNombre() : null,
            operario != null ? operario.getApellido() : null
        );
    }

    private Empleado resolveEmpleado(String username) {
        Persona persona = personaRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.USER_NOT_FOUND, "Usuario no encontrado"));
        if (!(persona instanceof Empleado empleado)) {
            throw new ForbiddenException(
                ErrorCode.NOT_EMPLOYEE, "Solo un empleado puede operar vehiculos");
        }
        return empleado;
    }
}
