-- ---------------------------------------------------------------------------
-- Anulacion de tickets desde el panel del administrador.
--
-- Un ticket es un registro CONTABLE (litros, precio, proveedor, fecha), asi que
-- no se borra fisicamente: se anula. Misma decision que personas.fecha_baja y
-- vehiculos.fecha_baja, y por el mismo motivo. Un borrado por error se puede
-- revertir y queda rastro de quien lo hizo.
--
-- `anulado_por` no es decorativo: si el dia de manana falta un ticket en una
-- rendicion, la pregunta va a ser quien lo saco y cuando.
--
-- Todas las columnas son NULLABLE y no hay backfill: los tickets existentes
-- quedan con NULL, que significa VIGENTE. Al momento de escribir esto
-- produccion tiene 3 tickets reales, y ninguno se toca.
-- ---------------------------------------------------------------------------

ALTER TABLE tickets ADD COLUMN fecha_anulacion timestamp(6) without time zone;

ALTER TABLE tickets ADD COLUMN anulado_por integer;

ALTER TABLE tickets ADD CONSTRAINT tickets_anulado_por_fkey
    FOREIGN KEY (anulado_por) REFERENCES personas (id);

-- Casi todas las consultas de tickets filtran "vigentes". El indice parcial
-- solo indexa las filas anuladas, que son la minoria, y mantiene barato el
-- listado del admin cuando pide ver los anulados.
CREATE INDEX tickets_fecha_anulacion_idx ON tickets (fecha_anulacion)
    WHERE fecha_anulacion IS NOT NULL;

COMMENT ON COLUMN tickets.fecha_anulacion IS
    'Cuando se anulo el ticket. NULL = vigente. El ticket no se borra: es un registro contable.';

COMMENT ON COLUMN tickets.anulado_por IS
    'Administrador que anulo el ticket. NULL mientras el ticket este vigente.';


-- ---------------------------------------------------------------------------
-- Consumo inicial del vehiculo: la estimacion que carga el admin en el alta.
--
-- Hasta ahora esa estimacion se guardaba en `consumo_promedio` y a partir de la
-- segunda carga el calculo real la PISABA, destruyendola. Mientras solo se
-- agregaban tickets no molestaba, porque el calculo real siempre es mejor dato.
--
-- Con la anulacion deja de alcanzar: si se anulan tickets hasta dejar el
-- vehiculo con menos de dos cargas, ya no hay con que calcular el consumo, y
-- sin esta columna `consumo_promedio` se quedaria con un numero derivado de
-- cargas que ya no existen. Un dato que sobrevive a la evidencia que lo
-- sustentaba.
--
-- Se guarda una vez en el alta y NO se vuelve a tocar. Es el valor al que se
-- vuelve cuando no hay cargas suficientes.
--
-- NULLABLE y sin backfill a proposito: para los vehiculos anteriores a V7 la
-- estimacion original ya fue pisada y no existe en ningun lado. Copiar el
-- `consumo_promedio` actual seria registrar un valor CALCULADO haciendolo pasar
-- por la estimacion del admin, que es exactamente la mentira que esta columna
-- viene a evitar.
-- ---------------------------------------------------------------------------

ALTER TABLE vehiculos ADD COLUMN consumo_inicial numeric(10,2);

COMMENT ON COLUMN vehiculos.consumo_inicial IS
    'Estimacion de consumo cargada por el admin en el alta. Nunca se pisa. Es el valor de respaldo cuando no hay cargas suficientes para calcular el consumo real. NULL en los vehiculos anteriores a V7.';
