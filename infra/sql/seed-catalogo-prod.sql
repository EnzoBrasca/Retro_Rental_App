-- ---------------------------------------------------------------------------
-- Catalogo real de proveedores y precios de combustible (produccion).
--
-- En el perfil `prod` el DataSeeder no corre, asi que proveedores y precios
-- se cargan con este script. Sin el, el formulario de tickets no resuelve el
-- precio por (proveedor, combustible) y el empleado ve el campo vacio.
--
-- Es idempotente y re-ejecutable: sirve tanto para la carga inicial como para
-- actualizar precios. Al reejecutarlo, los precios vigentes que cambiaron se
-- retiran (fecha_hasta = hoy) y se inserta la nueva fila vigente, conservando
-- la historia. Los que no cambiaron quedan intactos.
--
-- Uso:
--   docker compose -f docker-compose.prod.yml exec -T db \
--     psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" < infra/sql/seed-catalogo-prod.sql
--
-- Precios: relevamiento de surtidores de Cordoba capital, 30/07/2026.
-- Fuentes: hoydia.com.ar (YPF/Shell), combustibles.ar (Axion).
-- Los precios de GNC los aporto el cliente.
-- ---------------------------------------------------------------------------

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. Proveedores
--
-- CUIT real de cada razon social, dato del cliente (05/08/2026).
-- ---------------------------------------------------------------------------
INSERT INTO proveedores (nombre, cuit, servicio)
SELECT v.nombre, v.cuit, v.servicio
FROM (VALUES
    ('YPF En Ruta',      '33-71558362-9', 'COMBUSTIBLE'),
    ('SHELL Castiñeira', '30-54724011-8', 'COMBUSTIBLE'),
    ('Axion',            '30-50691900-9', 'COMBUSTIBLE')
) AS v (nombre, cuit, servicio)
WHERE NOT EXISTS (
    SELECT 1 FROM proveedores p WHERE p.nombre = v.nombre
);

-- Si el script ya corrio antes con el placeholder, esto lo deja al dia.
UPDATE proveedores SET cuit = '33-71558362-9' WHERE nombre = 'YPF En Ruta';
UPDATE proveedores SET cuit = '30-54724011-8' WHERE nombre = 'SHELL Castiñeira';
UPDATE proveedores SET cuit = '30-50691900-9' WHERE nombre = 'Axion';

-- ---------------------------------------------------------------------------
-- 2. Precios vigentes por (proveedor, combustible)
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE catalogo_nuevo (
    proveedor        varchar(255) NOT NULL,
    tipo_combustible varchar(255) NOT NULL,
    precio_unitario  numeric(10,2) NOT NULL
) ON COMMIT DROP;

INSERT INTO catalogo_nuevo (proveedor, tipo_combustible, precio_unitario) VALUES
    -- YPF: Super / Infinia / Diesel 500 / Infinia Diesel
    ('YPF En Ruta',      'NAFTA_SUPER',    2086.00),
    ('YPF En Ruta',      'NAFTA_PREMIUM',  2295.00),
    ('YPF En Ruta',      'GASOIL_GRADO_2', 2277.00),
    ('YPF En Ruta',      'GASOIL_GRADO_3', 2444.00),
    ('YPF En Ruta',      'GNC',             799.00),

    -- Shell: Super / V-Power / Evolux Diesel / V-Power Diesel
    ('SHELL Castiñeira', 'NAFTA_SUPER',    2179.00),
    ('SHELL Castiñeira', 'NAFTA_PREMIUM',  2479.00),
    ('SHELL Castiñeira', 'GASOIL_GRADO_2', 2369.00),
    ('SHELL Castiñeira', 'GASOIL_GRADO_3', 2599.00),
    ('SHELL Castiñeira', 'GNC',             799.00),

    -- Axion: Super / Quantium / Gasoil G2 / Quantium Diesel
    ('Axion',            'NAFTA_SUPER',    2159.00),
    ('Axion',            'NAFTA_PREMIUM',  2449.00),
    ('Axion',            'GASOIL_GRADO_2', 2399.00),
    ('Axion',            'GASOIL_GRADO_3', 2559.00),
    ('Axion',            'GNC',             849.00);

-- 2a. Retirar los vigentes cuyo precio cambio (conserva la historia).
UPDATE precios pr
SET fecha_hasta = CURRENT_DATE
FROM proveedores p, catalogo_nuevo c
WHERE pr.proveedor_id = p.id
  AND p.nombre = c.proveedor
  AND pr.servicio = 'COMBUSTIBLE'
  AND pr.tipo_combustible = c.tipo_combustible
  AND pr.fecha_hasta IS NULL
  AND pr.precio_unitario <> c.precio_unitario;

-- 2b. Insertar los que faltan (carga inicial o reemplazo de los recien retirados).
INSERT INTO precios (precio_unitario, servicio, tipo_combustible, proveedor_id, fecha_desde, fecha_hasta)
SELECT c.precio_unitario, 'COMBUSTIBLE', c.tipo_combustible, p.id, CURRENT_DATE, NULL
FROM catalogo_nuevo c
JOIN proveedores p ON p.nombre = c.proveedor
WHERE NOT EXISTS (
    SELECT 1
    FROM precios pr
    WHERE pr.proveedor_id = p.id
      AND pr.servicio = 'COMBUSTIBLE'
      AND pr.tipo_combustible = c.tipo_combustible
      AND pr.fecha_hasta IS NULL
);

COMMIT;

-- ---------------------------------------------------------------------------
-- Verificacion: deberia devolver 15 filas (3 proveedores x 5 combustibles).
-- ---------------------------------------------------------------------------
SELECT p.nombre, pr.tipo_combustible, pr.precio_unitario, pr.fecha_desde
FROM precios pr
JOIN proveedores p ON p.id = pr.proveedor_id
WHERE pr.fecha_hasta IS NULL
ORDER BY p.nombre, pr.tipo_combustible;
