-- ---------------------------------------------------------------------------
-- Proveedor generico: las cargas en estaciones que no se quieren registrar.
--
-- Hasta aca el selector de proveedor era un catalogo cerrado. Si el operario
-- cargaba en una estacion de ruta que la empresa NO quiere dar de alta como
-- proveedor frecuente, no tenia donde ponerla y la carga no se podia
-- registrar.
--
-- Esta migracion agrega UNA fila generica que absorbe todas esas cargas. No es
-- una estacion: es el balde donde cae lo que no es ninguna del catalogo. Por
-- eso no tiene CUIT -- no identifica a ninguna empresa --, y por eso el
-- desglose por proveedor del admin la muestra como una sola porcion "Otros",
-- sin abrir que estaciones hay adentro.
--
-- CONSECUENCIA sobre el catalogo de precios, que es la parte no obvia:
-- el invariante "un precio vigente por (proveedor, combustible)" pierde
-- sentido en esta fila. Dos cargas seguidas de "Otros" son en surtidores
-- DISTINTOS, asi que su vigente es el precio de otra estacion y no dice nada
-- del de esta carga. La regla vive en PrecioCatalogoService: para un proveedor
-- generico el precio NUNCA se hereda, siempre se tipea.
-- ---------------------------------------------------------------------------

ALTER TABLE proveedores
    ADD COLUMN generico boolean NOT NULL DEFAULT false;

-- El CUIT identifica a una empresa real y la fila generica no lo es. Se
-- permite NULL solo para ella: el CHECK mantiene la obligacion para todo
-- proveedor de verdad, que es donde el dato si sirve.
ALTER TABLE proveedores
    ALTER COLUMN cuit DROP NOT NULL;

ALTER TABLE proveedores
    ADD CONSTRAINT proveedores_cuit_check
        CHECK (generico OR cuit IS NOT NULL);

-- Una sola fila generica. Con dos, el formulario mostraria dos chips iguales y
-- el grafico del admin partiria en dos la misma porcion.
CREATE UNIQUE INDEX proveedores_generico_unico
    ON proveedores (generico)
    WHERE generico;

-- El nombre cumple doble funcion: es el chip que ve el operario en el
-- formulario Y la etiqueta que StatsService pone en el desglose por proveedor
-- (usa proveedor.nombre tal cual). Por eso "Otros" en plural: en el grafico,
-- "Otro" leeria como una estacion mas de la lista.
INSERT INTO proveedores (nombre, cuit, servicio, generico)
VALUES ('Otros', NULL, 'COMBUSTIBLE', true);
