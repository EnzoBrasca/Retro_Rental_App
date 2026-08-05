-- ---------------------------------------------------------------------------
-- Una maquina vial no tiene patente.
--
-- La columna `patente` era el identificador unico y visible de TODO vehiculo,
-- pero solo camiones y camionetas estan patentados. Las maquinas viales
-- (tipo_vehiculo = 'MAQUINA') se identifican por un numero interno que asigna
-- la empresa, y se describen por su modelo.
--
-- Se separan las dos cosas, que hasta ahora estaban colapsadas en un campo:
--
--   identificador -> QUE unidad es. Unico, obligatorio. Patente para camion y
--                    camioneta, numero interno (M-01, M-02, ...) para maquina.
--                    El FORMATO se deriva de tipo_vehiculo, no se guarda.
--   modelo        -> QUE cosa es. Descriptivo, repetible, sin unicidad. La
--                    empresa tiene dos maquinas del mismo modelo: por eso el
--                    modelo NO puede ser el identificador.
--
-- Se renombra en vez de agregar una columna nueva porque es el MISMO dato con
-- un nombre honesto: dejarlo como `patente` haria que el modelo mienta sobre
-- las maquinas, igual que `kilometraje` mentia sobre las horas en V3.
--
-- RENAME COLUMN preserva datos, tipo y constraints: no hay copia ni backfill.
-- El UNIQUE viaja con la columna (solo se renombra la constraint, que es
-- metadata). Los tickets NO se ven afectados: tickets.id_vehiculo apunta a
-- vehiculos.id, que no se toca.
--
-- `modelo` se agrega NULLABLE a proposito. Al momento de escribir esto
-- produccion tiene 1 vehiculo con 3 tickets reales, y no existe dato para
-- completarle el modelo: inventarlo seria peor que dejarlo vacio. La
-- obligatoriedad para las maquinas se valida en el backend al crear o editar,
-- donde el dato lo carga una persona que lo conoce.
-- ---------------------------------------------------------------------------

ALTER TABLE vehiculos RENAME COLUMN patente TO identificador;

ALTER TABLE vehiculos RENAME CONSTRAINT vehiculos_patente_key TO vehiculos_identificador_key;

ALTER TABLE vehiculos ADD COLUMN modelo character varying(60);

COMMENT ON COLUMN vehiculos.identificador IS
    'Identificador unico y visible del vehiculo: patente en CAMION/CAMIONETA, numero interno en MAQUINA. El formato se deriva de tipo_vehiculo.';

COMMENT ON COLUMN vehiculos.modelo IS
    'Modelo del vehiculo (ej. CAT 320D). Descriptivo y repetible: dos unidades pueden compartir modelo. Obligatorio para MAQUINA via validacion de aplicacion, nullable en el schema por los vehiculos previos a V6.';
