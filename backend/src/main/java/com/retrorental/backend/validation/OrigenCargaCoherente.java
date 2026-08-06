package com.retrorental.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Valida que una carga (ticket) tenga EXACTAMENTE un origen: vehiculo O
 * herramienta, nunca los dos ni ninguno; y que la lectura del contador
 * (usoAcumulado) solo venga cuando el origen es un vehiculo (una herramienta
 * no tiene contador ni horometro).
 *
 * Va a nivel de CLASE, mismo motivo que IdentificadorCoherente: la regla
 * depende de mas de un campo a la vez.
 */
@Documented
@Constraint(validatedBy = OrigenCargaCoherenteValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface OrigenCargaCoherente {

    String message() default "Origen de la carga incoherente";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
