package com.retrorental.backend.validation;

import com.retrorental.backend.model.enums.TipoVehiculo;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Aplica las dos reglas que dependen del tipo de vehiculo:
 *
 *  1. El identificador cumple el formato del tipo (patente vs numero interno).
 *  2. Una MAQUINA lleva modelo si o si.
 *
 * Las reglas no se escriben aca: se le preguntan a {@link TipoVehiculo}, que es
 * donde vive el conocimiento sobre que significa cada tipo. Este validator solo
 * las aplica y ubica el error en el campo correcto.
 */
public class IdentificadorCoherenteValidator
    implements ConstraintValidator<IdentificadorCoherente, VehiculoIdentificable> {

    @Override
    public boolean isValid(VehiculoIdentificable request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }

        TipoVehiculo tipo = request.getTipoVehiculo();
        if (tipo == null) {
            // Sin tipo no hay regla que aplicar. El @NotNull del campo ya
            // reporta el error; duplicarlo aca solo ensuciaria la respuesta.
            return true;
        }

        boolean valido = true;
        context.disableDefaultConstraintViolation();

        String identificador = request.getIdentificador();
        // Vacio o en blanco es problema del @NotBlank del campo, no de esta
        // regla: si lo reportaramos tambien aca, el mismo error saldria dos
        // veces con dos mensajes distintos.
        if (identificador != null && !identificador.isBlank()
            && !identificador.trim().matches(tipo.formatoIdentificador())) {

            addViolation(context, "identificador", String.format(
                "El %s debe tener entre %d y 10 caracteres alfanuméricos",
                tipo.nombreIdentificador(), tipo == TipoVehiculo.MAQUINA ? 2 : 5));
            valido = false;
        }

        String modelo = request.getModelo();
        if (tipo.requiereModelo() && (modelo == null || modelo.isBlank())) {
            addViolation(context, "modelo",
                "El modelo es obligatorio en una máquina: el número interno solo dice cuál es, no qué es");
            valido = false;
        }

        return valido;
    }

    // Cuelga el error de un campo puntual en vez de la clase entera, para que
    // salga en errors[].field y el formulario pueda marcar el input culpable.
    private void addViolation(ConstraintValidatorContext context, String campo, String mensaje) {
        context.buildConstraintViolationWithTemplate(mensaje)
            .addPropertyNode(campo)
            .addConstraintViolation();
    }
}
