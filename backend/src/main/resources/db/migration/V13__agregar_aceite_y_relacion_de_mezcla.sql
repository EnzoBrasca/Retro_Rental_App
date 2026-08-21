-- ---------------------------------------------------------------------------
-- El precio de la MEZCLA pasa a calcularse en vez de pedirse.
--
-- Nadie vende mezcla en un surtidor: no esta en el ticket, no esta en el
-- pico, no esta en ninguna etiqueta. Pedirle al empleado "el precio por litro
-- de la mezcla" era pedirle un numero que no existe, y que terminaba estimado
-- o copiado del de nafta pura.
--
-- Lo que SI se puede observar es el precio del aceite (esta en la botella) y
-- el de la nafta (ya esta en el catalogo). Con esos dos y la proporcion de la
-- herramienta, la mezcla sale de una cuenta:
--
--     precio_mezcla = precio_nafta * r/(r+1) + precio_aceite * 1/(r+1)
--
-- Esta migracion agrega las dos piezas que faltan para poder hacerla.
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- 1. precios.tipo_combustible: sumar ACEITE.
--
-- El aceite NO es un combustible, pero se cotiza como uno para reusar toda la
-- maquinaria del catalogo: un vigente unico por (proveedor, producto), el
-- historial por fecha_hasta y la banda que valida el alta. Modelarlo aparte
-- habria significado duplicar las tres cosas.
--
-- vehiculos_tipo_combustible_check queda intacto A PROPOSITO, igual que cuando
-- V8 agrego MEZCLA: ningun vehiculo carga aceite ni mezcla.
-- ---------------------------------------------------------------------------
ALTER TABLE precios DROP CONSTRAINT precios_tipo_combustible_check;

ALTER TABLE precios ADD CONSTRAINT precios_tipo_combustible_check
    CHECK (tipo_combustible IN
        ('NAFTA_SUPER', 'NAFTA_PREMIUM', 'GASOIL_GRADO_2', 'GASOIL_GRADO_3',
         'GNC', 'MEZCLA', 'ACEITE'));

-- ---------------------------------------------------------------------------
-- 2. herramientas.relacion_mezcla: la proporcion nafta:aceite de la maquina.
--
-- Se guarda el primer termino de la relacion: 50 = 50:1, o sea 50 partes de
-- nafta por 1 de aceite. Es una propiedad de la MAQUINA y no de la carga (una
-- motosierra vieja pide 25:1, una moderna 50:1), asi que vive en la herramienta
-- y no en el ticket: el operario no deberia tener que saberla de memoria ni
-- retipearla en cada carga.
--
-- NOT NULL con DEFAULT 50 en vez de nullable. Las herramientas que ya estan
-- cargadas quedan en 50, la proporcion mas comun en maquinas modernas, y el
-- admin corrige las que difieran desde el ABM. Dejarla nullable habria hecho
-- que la primera carga de mezcla sobre una herramienta existente muriera sin
-- relacion, y volver a trabar al operario es exactamente lo que se acaba de
-- sacar con el alta manual de precio.
--
-- El CHECK acota el dedazo: las relaciones reales van de 16:1 (muy viejas) a
-- 100:1, nunca 5000.
-- ---------------------------------------------------------------------------
ALTER TABLE herramientas
    ADD COLUMN relacion_mezcla integer NOT NULL DEFAULT 50;

ALTER TABLE herramientas ADD CONSTRAINT herramientas_relacion_mezcla_check
    CHECK (relacion_mezcla BETWEEN 1 AND 200);
