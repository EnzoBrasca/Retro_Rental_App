package com.retrorental.backend.model.enums;

public enum TipoVehiculo {
    MAQUINA,
    CAMIONETA,
    CAMION;

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
