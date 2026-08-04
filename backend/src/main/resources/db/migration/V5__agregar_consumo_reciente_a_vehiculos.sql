-- ---------------------------------------------------------------------------
-- Consumo calculado a partir de las cargas reales.
--
-- consumo_promedio pasa a calcularse solo sobre TODA la historia del vehiculo.
-- El valor que carga el administrador en el alta queda como estimacion inicial
-- y se sobreescribe en cuanto hay datos suficientes (dos cargas), asi que la
-- columna sigue siendo NOT NULL.
--
-- consumo_reciente es lo mismo pero sobre las ultimas N cargas
-- (app.consumo.ventana-cargas). Sirve para detectar cuando una maquina empieza
-- a desviarse de su propio promedio historico, que suele ser el primer sintoma
-- de una falla. Es NULLABLE: hasta la segunda carga no hay con que calcularlo.
--
-- Ambos se miden en la unidad del vehiculo: L/hora en una maquina vial,
-- L/100km en camiones y camionetas.
-- ---------------------------------------------------------------------------

ALTER TABLE vehiculos ADD COLUMN consumo_reciente numeric(10,2);

COMMENT ON COLUMN vehiculos.consumo_reciente IS
    'Consumo de las ultimas N cargas. NULL mientras no haya dos cargas con lectura.';
