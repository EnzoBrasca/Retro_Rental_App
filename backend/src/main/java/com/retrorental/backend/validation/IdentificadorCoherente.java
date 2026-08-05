package com.retrorental.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Valida que el identificador y el modelo sean coherentes con el tipo de
 * vehiculo del request.
 *
 * Va a nivel de CLASE y no de campo porque la regla depende de dos campos a la
 * vez: un @Pattern sobre `identificador` no puede mirar `tipoVehiculo` para
 * saber si tiene que exigir formato de patente o de numero interno.
 *
 * Al ser una constraint estandar de Bean Validation, los errores salen por el
 * mismo camino que el resto (BindException -> 400 VALIDATION_ERROR con la
 * lista de campos), sin una forma de respuesta nueva que el mobile tenga que
 * aprender a parsear.
 */
@Documented
@Constraint(validatedBy = IdentificadorCoherenteValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface IdentificadorCoherente {

    String message() default "Identificador incoherente con el tipo de vehiculo";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
