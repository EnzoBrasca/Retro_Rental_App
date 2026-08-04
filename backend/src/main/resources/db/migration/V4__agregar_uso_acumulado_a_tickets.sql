-- ---------------------------------------------------------------------------
-- Lectura del contador del vehiculo al momento de la carga.
--
-- Sin esto no se puede calcular consumo real: se sabe cuantos litros se
-- cargaron, pero no cuanto se uso el vehiculo entre carga y carga. La unidad es
-- la del vehiculo (horas en una maquina vial, kilometros en el resto): se
-- deriva de vehiculos.tipo_vehiculo, no se repite aca.
--
-- El ticket guarda la lectura como registro historico; ademas, al crear el
-- ticket el backend adelanta vehiculos.uso_acumulado a ese valor, para que la
-- ficha del vehiculo refleje siempre la ultima lectura conocida.
--
-- NULLABLE a proposito. La columna es obligatoria para los tickets NUEVOS (lo
-- exige @NotNull en CreateTicketRequest), pero los tickets anteriores a esta
-- feature no tienen el dato y NO se inventa: rellenarlos con el contador actual
-- del vehiculo seria fabricar una lectura que nunca se tomo. null significa
-- exactamente lo que paso: no se registro.
-- ---------------------------------------------------------------------------

ALTER TABLE tickets ADD COLUMN uso_acumulado integer;

COMMENT ON COLUMN tickets.uso_acumulado IS
    'Lectura del odometro/horometro del vehiculo al momento de la carga. '
    'NULL solo en tickets anteriores a la feature.';
