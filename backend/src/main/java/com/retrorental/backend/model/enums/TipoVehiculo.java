package com.retrorental.backend.model.enums;

public enum TipoVehiculo {
    MAQUINA,
    CAMIONETA,
    CAMION;

    // Patente argentina, en cualquiera de los dos formatos vigentes (AB123CD o
    // ABC123), tolerando guiones. Se mantiene el rango 5-10 que ya regia antes
    // de separar identificador de patente: apretarlo mas invalidaria vehiculos
    // ya cargados en produccion.
    private static final String FORMATO_PATENTE = "^[A-Za-z0-9-]{5,10}$";

    // Numero interno de una maquina. Mismo alfabeto, pero admite desde 2
    // caracteres porque la numeracion pensada para pintar en la carroceria es
    // corta (M-01). Con el minimo de 5 de una patente, "M-01" seria rechazado.
    private static final String FORMATO_INTERNO = "^[A-Za-z0-9-]{2,10}$";

    /**
     * Expresion regular que debe cumplir el identificador de este tipo.
     *
     * Vive aca por el mismo motivo que {@link #unidadUso()}: es una propiedad
     * del tipo de vehiculo, no de quien lo valide. Si estuviera como @Pattern
     * en los DTOs habria que repetirla en el alta y en la edicion, y ninguna de
     * las dos podria mirar el tipo para decidir.
     */
    public String formatoIdentificador() {
        return this == MAQUINA ? FORMATO_INTERNO : FORMATO_PATENTE;
    }

    /**
     * Como se llama el identificador de este tipo en los mensajes de error.
     * El mobile deriva su propia etiqueta desde tipoVehiculo; esto es solo para
     * que un 400 diga "patente" o "numero interno" y no "identificador".
     */
    public String nombreIdentificador() {
        return this == MAQUINA ? "numero interno" : "patente";
    }

    /**
     * Si este tipo exige modelo. Una maquina se identifica por un interno
     * inventado por la empresa: sin el modelo, nadie sabe que maquina es. Un
     * camion ya se identifica por su patente, asi que el modelo es opcional.
     */
    public boolean requiereModelo() {
        return this == MAQUINA;
    }

    /**
     * En que unidad se mide el uso acumulado de este tipo de vehiculo.
     *
     * Las maquinas viales miden HORAS: es lo que marca su horometro y lo que
     * define sus ciclos de mantenimiento. Camiones y camionetas usan el
     * odometro, en kilometros.
     *
     * La regla vive aca, en el enum, y no repartida entre services y pantallas:
     * es una propiedad del tipo de vehiculo, no de quien lo consulta.
     */
    public UnidadUso unidadUso() {
        return this == MAQUINA ? UnidadUso.HORAS : UnidadUso.KM;
    }
}
