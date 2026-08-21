package com.retrorental.backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Aplica las reglas de coherencia del origen de una carga:
 *
 *  1. Exactamente uno de idVehiculo/idHerramienta debe venir.
 *  2. usoAcumulado (lectura del contador) es obligatorio SOLO si el origen es
 *     un vehiculo: una herramienta no tiene contador ni horometro.
 *  3. Para un vehiculo tiene que venir idPrecio (el catalogo ya trae el precio
 *     resuelto) O BIEN precioUnitario: cuando el proveedor todavia no tiene
 *     precio de ese combustible no hay idPrecio que elegir en el formulario, y
 *     el empleado lo tipea a mano. tipoCombustible NO se le puede mandar en
 *     ningun caso: el combustible de un vehiculo es fijo y el service lo lee de
 *     su ficha.
 *  4. tipoCombustible es obligatorio SOLO para una herramienta (lo elige en
 *     el momento de la carga) e idPrecio NO se le puede mandar (no hay uno
 *     resuelto de antemano: lo resuelve el service, ver
 *     TicketService.resolvePrecioPorCombustible).
 */
public class OrigenCargaCoherenteValidator
    implements ConstraintValidator<OrigenCargaCoherente, CargaOrigen> {

    @Override
    public boolean isValid(CargaOrigen request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }

        Integer idVehiculo = request.getIdVehiculo();
        Integer idHerramienta = request.getIdHerramienta();
        boolean valido = true;
        context.disableDefaultConstraintViolation();

        if (idVehiculo == null && idHerramienta == null) {
            addViolation(context, "idVehiculo",
                "Debe indicar un vehiculo o una herramienta");
            valido = false;
        } else if (idVehiculo != null && idHerramienta != null) {
            addViolation(context, "idHerramienta",
                "No se puede indicar vehiculo y herramienta a la vez");
            valido = false;
        } else if (idVehiculo != null && request.getUsoAcumulado() == null) {
            addViolation(context, "usoAcumulado",
                "La lectura del contador es obligatoria para un vehiculo");
            valido = false;
        } else if (idHerramienta != null && request.getUsoAcumulado() != null) {
            addViolation(context, "usoAcumulado",
                "Una herramienta no tiene contador: no se debe indicar la lectura");
            valido = false;
        } else if (idVehiculo != null
            && request.getIdPrecio() == null && request.getPrecioUnitario() == null) {
            addViolation(context, "idPrecio",
                "Para un vehiculo se debe indicar el precio del catalogo o el precio por litro");
            valido = false;
        } else if (idVehiculo != null && request.getTipoCombustible() != null) {
            addViolation(context, "tipoCombustible",
                "Un vehiculo tiene el combustible fijo: no se debe indicar tipoCombustible");
            valido = false;
        } else if (idHerramienta != null && request.getTipoCombustible() == null) {
            addViolation(context, "tipoCombustible",
                "El combustible es obligatorio para una herramienta");
            valido = false;
        } else if (idHerramienta != null && request.getIdPrecio() != null) {
            addViolation(context, "idPrecio",
                "Una herramienta no manda idPrecio: indique tipoCombustible");
            valido = false;
        }

        return valido;
    }

    private void addViolation(ConstraintValidatorContext context, String campo, String mensaje) {
        context.buildConstraintViolationWithTemplate(mensaje)
            .addPropertyNode(campo)
            .addConstraintViolation();
    }
}
