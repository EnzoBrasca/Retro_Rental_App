-- ---------------------------------------------------------------------------
-- Indices faltantes en `tickets`, la tabla que mas crece del sistema (una fila
-- por cada carga de combustible, y nunca se borra: los anulados quedan).
--
-- Los tres indices de abajo cubren las tres consultas calientes que hoy no
-- tienen ninguno que las soporte. Ver docs/BACKEND-AUDIT.md (DB-03).
--
-- SIN `CONCURRENTLY` a proposito. Al momento de escribir esto produccion tiene
-- del orden de 3 tickets reales, asi que un CREATE INDEX comun bloquea la tabla
-- por milisegundos. `CONCURRENTLY` obligaria a marcar esta migracion con
-- `executeInTransaction=false` en un archivo .conf aparte, y si falla deja el
-- indice en estado INVALID que hay que limpiar a mano. Es maquinaria que hoy no
-- compra nada.
--
-- QUE HACER EN EL FUTURO: cuando `tickets` tenga volumen real (decenas de miles
-- de filas), cualquier indice NUEVO sobre esta tabla si debe crearse con
-- CONCURRENTLY + executeInTransaction=false. Esta migracion ya habra corrido, no
-- se toca.
-- ---------------------------------------------------------------------------


-- ---------------------------------------------------------------------------
-- 1) FK sin indice.
--
-- `id_proveedor` es la unica FK de `tickets` que quedo sin indice: id_persona e
-- id_vehiculo lo recibieron en V1 e id_herramienta en V8. Fue un olvido, no una
-- decision: el filtro por proveedor del listado del admin
-- (TicketService.listForAdmin) hace un scan por esta columna.
--
-- Ademas de las consultas, Postgres necesita este indice para validar la FK de
-- forma barata cuando se borra o actualiza un proveedor.
-- ---------------------------------------------------------------------------
CREATE INDEX tickets_id_proveedor_idx ON tickets (id_proveedor);


-- ---------------------------------------------------------------------------
-- 2) Historial del empleado (pestania "Historial" de la app).
--
-- Consulta: TicketRepository.findVigentesDePersona -> filtra por id_persona +
-- fecha_anulacion IS NULL y ordena por fecha_carga DESC.
--
-- Hoy existen `tickets_id_persona_idx` y `tickets_fecha_carga_idx` por
-- separado, y ninguno de los dos resuelve bien esta consulta: Postgres usa uno,
-- filtra el resto en memoria y despues ORDENA. Con el indice compuesto el orden
-- ya viene dado por el indice y desaparece el sort.
--
-- OJO con `tickets_fecha_anulacion_idx` (V7): ese indice parcial indexa solo las
-- filas ANULADAS (WHERE fecha_anulacion IS NOT NULL). Para esta consulta, que
-- busca exactamente lo contrario, NO sirve. De ahi que este sea parcial sobre
-- IS NULL: los vigentes son la enorme mayoria, pero indexar solo esos mantiene
-- el indice mas chico y le avisa al planner que la condicion ya esta cubierta.
--
-- El DESC importa: el indice se recorre en el mismo orden en que se devuelve.
-- ---------------------------------------------------------------------------
CREATE INDEX tickets_persona_vigentes_idx
    ON tickets (id_persona, fecha_carga DESC)
    WHERE fecha_anulacion IS NULL;


-- ---------------------------------------------------------------------------
-- LO QUE NO SE AGREGO ACA, Y POR QUE.
--
-- El recalculo de consumo
-- (findByVehiculoIdAndUsoAcumuladoIsNotNullAndFechaAnulacionIsNullOrderByUsoAcumuladoAsc,
-- el camino de escritura mas caliente: corre en cada create y en cada anular)
-- tambien esta sin indice compuesto. Se probo agregarle uno equivalente a los
-- de arriba y NO se gana el sueldo: medido sobre 200.000 tickets, el planner
-- sigue prefiriendo `tickets_id_vehiculo_idx` y, forzandolo, la mejora es de
-- 1,53 ms a 1,09 ms a cambio de 4,5 MB de indice. Peso muerto.
--
-- El problema de esa consulta NO es el indice: es que trae entidades `Ticket`
-- COMPLETAS cuando el calculo solo necesita (uso_acumulado, litros). Mientras
-- se traiga la entidad entera, Postgres tiene que ir al heap si o si y ningun
-- indice lo evita.
--
-- La solucion real es una PROYECCION en el repositorio + un indice CUBRIDOR:
--
--   CREATE INDEX tickets_consumo_cubridor_idx ON tickets (id_vehiculo, uso_acumulado)
--       INCLUDE (litros) WHERE fecha_anulacion IS NULL AND uso_acumulado IS NOT NULL;
--
-- Medido sobre los mismos 200.000 tickets, eso da un Index Only Scan con
-- Heap Fetches: 0 -> de 2.074 buffers y 1,53 ms a 16 buffers y 0,19 ms.
--
-- No entra en esta migracion porque sin el cambio de la consulta el indice
-- quedaria sin usar. Los dos tienen que viajar juntos. Ver docs/BACKEND-AUDIT.md.
-- ---------------------------------------------------------------------------
