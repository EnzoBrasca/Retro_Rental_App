# Backend — Cambios de la sesión (2026-07-07)

Resumen de las implementaciones y decisiones de arquitectura realizadas en el
backend (Spring Boot / Java) durante esta sesión. El objetivo fue cerrar el
apartado de vehículos y estadísticas, y corregir un vacío estructural en la
relación entre tickets y vehículos.

> **Stack real del backend:** Java + Spring Boot + Spring Data JPA (Hibernate)
> sobre PostgreSQL dockerizado. (Algunas tarjetas viejas de Notion mencionaban
> Node.js/Express/Prisma; eso fue un planteo inicial descartado y ya se corrigió
> en el board.)

---

## Índice

1. [Vinculación Ticket ↔ Vehículo](#1-vinculación-ticket--vehículo)
2. [Listado de vehículos (lectura)](#2-listado-de-vehículos-lectura)
3. [Estadísticas de consumo (diario / semanal / mensual)](#3-estadísticas-de-consumo)
4. [ABM de vehículos (administrador)](#4-abm-de-vehículos-administrador)
5. [Resumen de endpoints nuevos](#5-resumen-de-endpoints-nuevos)
6. [Migraciones de base de datos pendientes](#6-migraciones-de-base-de-datos-pendientes)
7. [Convenciones y notas transversales](#7-convenciones-y-notas-transversales)

---

## 1. Vinculación Ticket ↔ Vehículo

### Problema detectado
El modelo `Ticket` no tenía relación con `Vehiculo`. Se evaluó derivar el
vehículo a través de `Empleado`, pero **no alcanza**:

- `Empleado.vehiculos` es una relación `@ManyToMany` (tabla `empleado_vehiculo`),
  o sea "qué vehículos puede operar el empleado", no "cuál cargó en este ticket".
- Es **mutable**: si mañana se reasigna un vehículo, los tickets históricos
  quedarían apuntando a una asignación distinta a la real del momento.

Un ticket es un **hecho histórico** y necesita su propio dato fijo.

### Solución
FK directa `Ticket → Vehiculo` (`id_vehiculo`, `NOT NULL`), siguiendo el mismo
patrón que las FK ya existentes (`precio`, `proveedor`, `empleado`). La
asignación `empleado_vehiculo` se mantiene, pero se usa **solo como
autorización**: un empleado solo puede cargar un ticket sobre un vehículo que
tiene asignado.

### Archivos tocados
- `model/Ticket.java` — nuevo `@ManyToOne Vehiculo vehiculo` (`id_vehiculo`, `nullable = false`).
- `dto/request/CreateTicketRequest.java` — campo `idVehiculo` (`@NotNull`).
- `dto/response/TicketResponse.java` — expone `idVehiculo`.
- `service/TicketService.java`:
  - `resolveVehiculoAsignado()` — valida existencia (**404**) y pertenencia a
    los vehículos del empleado (**403**).
  - `create()` — asocia el vehículo al ticket.
  - `listForAdmin()` — nuevo filtro opcional `vehiculoId` + `fetch join` a
    `vehiculo` (evita N+1).
  - `toResponse()` — mapea `idVehiculo`.
- `controller/AdminTicketController.java` — nuevo `@RequestParam vehiculoId` opcional.

---

## 2. Listado de vehículos (lectura)

Se agregó el listado de maquinaria y se consolidó la lectura de vehículos en un
único controller.

### Decisiones
- `GET /vehiculos` **devuelve todos** los vehículos, sin filtrar por estado. El
  enum `Estado` es `DISPONIBLE / EN_USO / EN_MANTENIMIENTO`; **ninguno es un
  "inactivo"**, así que el estado viaja en cada item del DTO y el cliente decide.
- Ruta en **español** (`/vehiculos`), siguiendo la convención del proyecto
  (la tarjeta de Notion decía `/vehicles` a modo de etiqueta).
- Acceso: cualquier usuario autenticado (cae en `anyRequest().authenticated()`
  de `SecurityConfig`).

### Archivos tocados
- `controller/VehiculoController.java` — se quitó el `@RequestMapping` de clase
  para alojar dos rutas de raíz distinta:
  - `GET /vehiculos` — catálogo completo.
  - `GET /me/vehiculos` — vehículos asignados al empleado autenticado (para el
    selector del formulario de carga, en el mobile).
- `service/VehiculoService.java` — `listAll()`.
- `dto/response/VehiculoResponse.java` — DTO reducido (id, patente, tipoVehiculo,
  tipoCombustible, estado, fechaBaja).

---

## 3. Estadísticas de consumo

API de estadísticas de combustible para el panel del Jefe: diario, semanal y
mensual. Los tres comparten **toda** la lógica de agregación; solo cambia el
rango `[desde, hasta)`.

### Cómo se calcula el gasto
Por ticket: `gasto = litros × precio.precioUnitario` (el precio vigente que
quedó asociado al ticket). La agregación se hace **en memoria** (no con `SUM` de
JPQL) por dos razones: el volumen por período es chico, y evita mezclar `Double`
(litros) con `BigDecimal` (precio) en SQL.

### Respuesta (`StatsResponse`)
| Campo | Detalle |
|---|---|
| `desde` / `hasta` | Límites del período (ambos **inclusivos**). |
| `totalLitros` | Suma de litros. |
| `gastoTotal` | Suma de gasto (BigDecimal, 2 decimales HALF_UP). |
| `cantidadRegistros` | Cantidad de tickets. |
| `vehiculosActivos` | Vehículos distintos con carga en el período. |
| `promedioLitrosPorVehiculo` | `totalLitros / vehiculosActivos` (0 si no hay). |
| `desglosePorVehiculo` | Lista de `VehiculoConsumo`, **ordenada por litros desc** → es el ranking de "top consumidores" (sin campo redundante). |

`VehiculoConsumo`: `vehiculoId`, `patente`, `litros`, `gasto`.

### Períodos
- **Diario:** `[fecha, fecha+1día)` (default hoy).
- **Semanal:** semana ISO lunes–domingo que contiene la fecha.
- **Mensual:** mes calendario que contiene la fecha.

### Seguridad
Bajo `/admin/stats/**` → hereda `hasRole("ADMINISTRADOR")` de `SecurityConfig`
(son datos agregados de toda la flota).

### Archivos nuevos
- `dto/response/StatsResponse.java`
- `dto/response/VehiculoConsumo.java`
- `service/StatsService.java` — `daily()` / `weekly()` / `monthly()` delegan en
  `statsForRange(desdeInclusive, hastaExclusive)`.
- `controller/StatsController.java` — `@RequestMapping("/admin/stats")`.
- `repository/TicketRepository.java` — `findForStats(desde, hasta)` con
  `JOIN FETCH` a `precio` y `vehiculo`, límite superior **exclusivo**.

---

## 4. ABM de vehículos (administrador)

Alta, edición y baja de vehículos, reservado al administrador. Se adelantó el
backend para tenerlo listo antes del mobile.

### Decisiones clave
1. **Baja lógica, no física.** `Ticket.id_vehiculo` es FK `NOT NULL`: un
   vehículo con tickets no se puede borrar sin romper la integridad. El `DELETE`
   **desactiva** seteando `fechaBaja`.
2. **Se siguió la convención existente `Empleado.fechaBaja`** (activo = `null`)
   en vez de agregar un valor `BAJA` al enum `Estado`. Así el **ciclo de vida**
   (activo/baja) queda separado del **estado operativo**
   (`DISPONIBLE/EN_USO/EN_MANTENIMIENTO`) — son conceptos distintos.

### Reglas de negocio
- **Alta (`POST`):** patente única → `409` si ya existe. `estado` opcional
  (default `DISPONIBLE`).
- **Edición (`PUT`):** reemplazo completo. Si cambia la patente, se valida que
  no colisione con **otro** vehículo → `409`.
- **Baja (`DELETE`):** setea `fechaBaja`. `409` si ya estaba dado de baja.

### Archivos tocados
- `model/Vehiculo.java` — nuevo campo `fechaBaja` (LocalDate, nullable).
- `dto/request/CreateVehiculoRequest.java` (nuevo)
- `dto/request/UpdateVehiculoRequest.java` (nuevo)
- `dto/response/VehiculoResponse.java` — +`fechaBaja`.
- `service/VehiculoService.java` — `create()`, `update()`, `desactivar()`.
- `controller/AdminVehiculoController.java` (nuevo) — `@RequestMapping("/admin/vehiculos")`.

---

## 5. Resumen de endpoints nuevos

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| `GET` | `/vehiculos` | Autenticado | Catálogo completo de maquinaria. |
| `GET` | `/me/vehiculos` | Autenticado (empleado) | Vehículos asignados al usuario. |
| `GET` | `/admin/stats/daily` | Administrador | Estadísticas del día (`?fecha=`). |
| `GET` | `/admin/stats/weekly` | Administrador | Estadísticas de la semana (`?fecha=`). |
| `GET` | `/admin/stats/monthly` | Administrador | Estadísticas del mes (`?fecha=`). |
| `GET` | `/admin/vehiculos` | Administrador | Lista todos (incl. baja). |
| `POST` | `/admin/vehiculos` | Administrador | Alta de vehículo. |
| `PUT` | `/admin/vehiculos/{id}` | Administrador | Edición completa. |
| `DELETE` | `/admin/vehiculos/{id}` | Administrador | Baja lógica. |

**Modificados:** `POST /tickets` (ahora requiere `idVehiculo`),
`GET /admin/tickets` (nuevo filtro `vehiculoId`).

---

## 6. Migraciones de base de datos pendientes

El esquema **no** usa Flyway/Liquibase. Comportamiento por profile:
- **dev** → `ddl-auto: update` (Hibernate ajusta el esquema solo).
- **prod** → `ddl-auto: validate` (no toca nada; **la app no arranca** si el
  esquema no coincide).

Cambios de esquema introducidos esta sesión:

```sql
-- Tickets: FK al vehículo cargado (NOT NULL).
-- En dev, si la tabla tickets YA tiene filas, el ALTER NOT NULL falla:
-- vaciar la tabla (TRUNCATE tickets) o recrear el volumen antes de reiniciar.
ALTER TABLE tickets
    ADD COLUMN id_vehiculo INTEGER NOT NULL REFERENCES vehiculos(id);

-- Vehículos: baja lógica (nullable, no rompe con filas existentes).
ALTER TABLE vehiculos
    ADD COLUMN fecha_baja DATE;
```

En **dev** ambos se aplican automáticamente al reiniciar (salvo la salvedad del
`NOT NULL` sobre `tickets` con datos previos). En **prod** hay que correrlos a
mano antes de desplegar.

---

## 7. Convenciones y notas transversales

- **Rutas en español** (`/vehiculos`, `/admin/vehiculos`, ...), acorde al
  dominio del proyecto.
- **Seguridad por prefijo:** todo lo que cuelga de `/admin/**` queda restringido
  a `ADMINISTRADOR` en `SecurityConfig`; los controllers de admin **no**
  declaran el rol individualmente.
- **DTOs de respuesta = `record`**, un archivo por record.
- **Excepciones de dominio** mapeadas por `GlobalExceptionHandler`:
  `ResourceNotFoundException` (404), `ForbiddenException` (403),
  `ConflictException` (409).
- **Fotos en MinIO:** se persiste la *key*; la URL presignada se deriva en cada
  lectura (no se guarda).
- **Ciclo de vida (baja lógica):** patrón `fechaBaja` nullable, `null` = activo
  (compartido por `Empleado` y ahora `Vehiculo`).

### Estado de verificación
Todos los cambios **compilan** (`./mvnw compile`, exit 0). No hay tests
automatizados que cubran estos endpoints todavía (existe solo el test de
contexto por defecto); la tarjeta de tests unitarios sigue pendiente en Notion.
