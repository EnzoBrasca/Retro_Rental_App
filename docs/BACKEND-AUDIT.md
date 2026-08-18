# Auditoría de calidad de código — Backend

Fecha de la auditoría: 2026-08-14
Alcance: `backend/` completo — 133 archivos Java, ~10.650 líneas. Capa web (controllers,
DTOs, manejo de errores), capa de servicios y validación, capa de persistencia (entidades,
repositorios, 9 migraciones Flyway), suite de tests, build (`pom.xml`, `dockerfile`) y
scripts.

**Esta auditoría NO cubre seguridad.** Eso ya está en `docs/SECURITY-AUDIT.md` y sigue
siendo el documento de referencia para vulnerabilidades. Acá se audita calidad: código
muerto, ineficiencias, malas prácticas, estructuras mal resueltas y deuda técnica.

## Cómo usar este documento

Mismo criterio que la auditoría de seguridad: cada hallazgo tiene una casilla. Al
implementar el arreglo, marcá la casilla y agregá el commit o PR donde se resolvió. Lo que
sigue sin marcar es lo que falta.

Los IDs llevan prefijo por capa para que sea fácil ubicarlos: `TX` (transacciones), `DB`
(persistencia), `SVC` (servicios), `WEB` (capa web), `TST` (tests), `BLD` (build), `INF`
(infra/scripts).

## Contexto de escala — leer antes de priorizar

Producción hoy maneja un volumen chico (del orden de un puñado de vehículos y decenas de
tickets). **Casi nada de lo que sigue está roto hoy.** Lo que sigue son minas plantadas:
problemas cuyo costo es invisible con estos datos y se vuelve grave cuando la base crece
o cuando entran varios empleados en paralelo.

Eso cambia cómo hay que leer las severidades. "Crítico" acá no significa "está fallando
ahora", significa **"esto se rompe solo con que el negocio crezca, y arreglarlo después
cuesta mucho más que arreglarlo ahora"**. Es la diferencia entre reparar una viga antes de
levantar el segundo piso y hacerlo después.

## Estado general

| Severidad | Total | Resueltas | Pendientes |
| --- | --- | --- | --- |
| Crítica | 3 | 2 | 1 |
| Alta | 5 | 3 | 2 |
| Media | 11 | 0 | 11 |
| Baja | 11 | 0 | 11 |

**Fase 1 completada** (rama `perf/fase-1-indices-y-fetch-joins`): resueltos DB-00, DB-01,
DB-02, DB-03 y DB-05. Suite en 207/207, con 3 tests nuevos contra Postgres real.

## Lo que ya está bien (no tocar)

Esto se documenta explícitamente para que un refactor futuro no lo revierta sin querer.
La auditoría encontró bastante menos de lo que esperaba, y eso es mérito del código:

**Capa web** — es la capa más limpia del backend. Cero lógica de negocio en los
controllers (todos son delegación de una línea al servicio). Cero acceso a repositorios
desde controllers. Cero entidades filtradas en respuestas: todo sale por DTO. `@Valid`
presente en los 13 controllers sin excepción. Las validaciones de los request DTOs son
minuciosas (`@NotBlank`, `@Pattern`, `@Size`, `@Positive`, `@PastOrPresent` bien aplicados
campo por campo). Los 40 valores del enum `ErrorCode` se emiten en algún lado — ninguno
quedó huérfano. Ninguna de las 6 excepciones de dominio quedó sin uso.

**Persistencia** — `open-in-view: false`. `ddl-auto: validate` en todos los perfiles: el
schema es propiedad de Flyway y nunca se toca solo. Todos los `@Enumerated` son
`EnumType.STRING`, así que reordenar un enum no corrompe datos. Los renames de V3
(`kilometraje`→`uso_acumulado`) y V6 (`patente`→`identificador`) usaron `RENAME COLUMN`
limpio: **no quedó una sola columna zombie** de esos refactors, verificado migración por
migración contra los campos de cada entidad. `VehiculoRepository.findAll()` ya usa
`@EntityGraph` correctamente — es el patrón a copiar para los N+1 que sí quedaron.

**Servicios** — `MistralTicketAnalysisService` tiene un bulkhead bien hecho: `Semaphore`
con orden justo, release garantizado en `finally`, timeouts explícitos, restauración del
flag de interrupción. `ImageValidator` y `MinioStorageService` no filtran streams
(try-with-resources en ambos) y derivan el content-type del formato detectado, nunca del
cliente. No hay un solo `catch (Exception e)` que se trague el error: todos relanzan como
excepción de dominio con causa, o son fallbacks de parseo documentados.

**Tests** — la suite es de calidad por encima del promedio para un proyecto de este
tamaño. Los tests assertean el cuerpo de la respuesta, no solo el status. Usan slices
(`@WebMvcTest`, Mockito puro) donde corresponde: `@SpringBootTest` se usa en una sola
clase y está documentado por qué. Varios tests codifican invariantes de seguridad
(mensajes de error indistinguibles, 404-en-vez-de-403 en checks de propiedad) **con
comentarios que explican el porqué**. Eso es lo que separa un test que protege de un test
que solo sube el número de cobertura.

**Build e infra** — el `dockerfile` está bien resuelto: multi-stage real (la imagen final
es `21-jre`, no `-jdk`), cacheo de capas correcto (`pom.xml` + `dependency:go-offline`
antes de copiar `src/`), y corre como usuario no-root (`spring:spring`). `DataSeeder` está
guardado con `@Profile("dev")` y es idempotente: no corre en producción. `test-affected.sh`
tiene `set -euo pipefail` y las variables están correctamente quoteadas.

---

# CRÍTICAS

## [x] DB-00 — El panel del admin NO muestra los tickets de herramienta (pérdida de datos silenciosa)

> **Hallazgo POSTERIOR a la auditoría.** No salió del análisis estático: apareció al abrir
> `listForAdmin` durante la Fase 1 para copiar su patrón de paginación. Vale registrarlo
> como lo que es — una auditoría estática encontró 29 cosas y se le pasó la única que ya
> estaba rompiendo datos hoy.

**Ubicación:** `backend/src/main/java/com/retrorental/backend/service/TicketService.java:582`
(dentro de la `Specification` de `listForAdmin`)

**Descripción:** la Specification hacía `root.fetch("vehiculo")` sin `JoinType`. El default
de `fetch()` en la Criteria API es **INNER JOIN**. Como un ticket tiene vehículo O
herramienta (nunca los dos), un INNER JOIN contra `vehiculos` **elimina del resultado todos
los tickets de herramienta**, que tienen `id_vehiculo` en NULL.

**Impacto:** el administrador no ve ni una sola carga de motosierra o bidón en el panel de
tickets. No hay error, no hay warning, no hay log: la query simplemente devuelve menos
filas. Combustible cargado, pagado y registrado que es invisible en la única pantalla donde
se controla. Y como el listado del admin es la herramienta de rendición, eso es plata que
no se puede auditar.

**Verificado empíricamente** sobre 200.000 tickets en un Postgres real, con un ticket de
herramienta entre ellos:

| Variante | Tickets visibles |
| --- | --- |
| `INNER JOIN` (lo que había) | 196.000 |
| `LEFT JOIN` (correcto) | 196.001 |

El ticket de herramienta, específicamente: 0 con INNER, 1 con LEFT.

**Lo que más duele:** el comentario de `TicketRepository.findForStats` advierte de esta
trampa **con todas las letras** — "un INNER JOIN sobre cualquiera de las dos EXCLUIRÍA de
las estadísticas los tickets del otro origen". El equipo la conocía, la documentó, y cayó
en ella igual en el archivo de al lado. Es la evidencia más fuerte del patrón que cierra
este documento.

**Mitigación aplicada:** `JoinType.LEFT` explícito en `vehiculo`, y `herramienta` agregada
al fetch (antes ni se traía, o sea que además era un N+1 para esos tickets).

**Test de regresión:** `TicketPersistenciaTest.listForAdmin_incluyeLosTicketsDeHerramientaYNoSoloLosDeVehiculo`.
Se verificó que **falla** al revertir el arreglo (`Expected size: 2 but was: 1`); un test de
regresión que pasa en ambos casos no sirve de nada.

## [ ] TX-01 — I/O de red (MinIO y OCR) dentro de transacciones: agota el pool de conexiones

**Ubicación:**
- `backend/src/main/java/com/retrorental/backend/service/TicketService.java:72-185`
  (`create()`, uploads a MinIO en líneas 149 y 153)
- `backend/src/main/java/com/retrorental/backend/service/TicketService.java:301-337`
  (`analyze()`, llamada HTTP a Mistral en línea 317)
- `backend/src/main/java/com/retrorental/backend/config/MinioConfig.java:20-37`
  (sin timeouts explícitos)
- `application.yml` / `application-prod.yml` (sin sizing de Hikari)

**Descripción:** los dos métodos están anotados `@Transactional` y hacen I/O de red
bloqueante adentro. `create()` sube dos fotos a MinIO. `analyze()` llama a la API de OCR de
Mistral, que tiene un timeout configurado de hasta 30 segundos. Durante todo ese tiempo la
transacción está abierta y **la conexión de base de datos queda retenida sin hacer nada**.

**Por qué esto es crítico y no "medio":** los tres factores se multiplican.

1. Hikari no está configurado en ningún perfil, así que corre con el default: **10
   conexiones máximo** (ver DB-09).
2. El bulkhead de Mistral permite **5 llamadas OCR concurrentes**.
3. Cada una de esas 5 retiene una conexión hasta 30 segundos.

O sea: **cinco empleados sacando foto de un ticket al mismo tiempo se comen la mitad del
pool durante medio minuto.** El resto de la aplicación —login, listados, estadísticas del
panel del jefe— compite por las 5 conexiones que quedan. Y si MinIO se pone lento, se
suma: `MinioConfig` no configura timeouts, así que un MinIO colgado bloquea
**indefinidamente** (`MistralConfig` sí los configura — la asimetría es el bug).

Esto es autoinfligido: la app se cae sola sin que nadie la ataque, solo con que la usen
como está pensada para ser usada.

**Arreglo:**
1. Sacar el I/O de la transacción. En `create()`: subir los archivos **antes** de abrir la
   transacción (los uploads no dependen de nada de la DB, resuelven a keys de bytes), y
   dejar adentro del `@Transactional` únicamente las escrituras. En `analyze()`: llamar al
   OCR fuera de toda transacción y envolver solo `resolveProveedor`/`resolvePrecio` en un
   bloque transaccional corto.
2. Configurar timeouts explícitos de connect/read/write en los beans de `MinioConfig`,
   espejando lo que ya hace `MistralConfig.java:27-30`.
3. Fijar el tamaño del pool de Hikari explícitamente en vez de heredar el default (ver
   DB-09).

Los tres puntos son el mismo problema. Arreglar solo uno deja la mina activa.

## [x] DB-01 — `GET /tickets/me`: N+1 sobre una lista sin paginar

**Ubicación:**
- `backend/src/main/java/com/retrorental/backend/service/TicketService.java:671-676`
  (`listMine`), mapeo en `:728-758` (`toResponse`)
- `backend/src/main/java/com/retrorental/backend/controller/TicketController.java:48-51`
- `backend/src/main/java/com/retrorental/backend/repository/TicketRepository.java:35`

**Descripción:** dos defectos que por separado serían "medio" y juntos se multiplican.

`findByPersonaIdAndFechaAnulacionIsNullOrderByFechaCargaDesc` no tiene `JOIN FETCH`. El
resultado se mapea con `toResponse`, que dereferencia seis relaciones `@ManyToOne(LAZY)`:
`precio`, `proveedor`, `vehiculo`, `herramienta`, `persona`, `anuladoPor`. **Cada ticket
dispara hasta 6 SELECTs adicionales.**

Y el endpoint devuelve un `List<TicketResponse>` sin paginar, sobre el historial completo
del empleado, que crece con cada carga de combustible y no se borra nunca.

Un empleado con 500 cargas acumuladas en dos años dispara **hasta 3.001 queries en un solo
request**. Y ese request lo hace la app móvil, desde el teléfono del empleado, con la
conexión de red que haya en el yacimiento.

**Lo que más duele:** el patrón correcto ya existe en el mismo archivo. `listForAdmin`
(línea 555-617) usa `Pageable`/`PagedModel` **y** `root.fetch(...)` en su Specification.
`TicketRepository.findForStats` (línea 58-67) usa `JOIN FETCH`. Simplemente se pasó por
alto en el camino del empleado — que es el que más se usa.

**Arreglo:**
1. Query con fetch join:
   ```java
   @Query("""
       SELECT t FROM Ticket t
       JOIN FETCH t.precio
       JOIN FETCH t.proveedor
       JOIN FETCH t.persona
       LEFT JOIN FETCH t.vehiculo
       LEFT JOIN FETCH t.herramienta
       WHERE t.persona.id = :personaId AND t.fechaAnulacion IS NULL
       ORDER BY t.fechaCarga DESC
       """)
   ```
2. Paginar el endpoint con `Pageable`, igual que `listForAdmin`.
3. Agregar el índice parcial de DB-03 que soporta exactamente esta query.

Los tres van juntos: paginar sin el fetch join deja el N+1 por página, y el fetch join sin
índice sigue haciendo sort en memoria.

**Mitigación aplicada.** Los tres puntos, más el cambio en `mobile/`. Una corrección al
texto original: son **cinco** relaciones LAZY por ticket, no seis. `anuladoPor` no cuenta
porque esta consulta filtra vigentes, así que esa FK viene NULL y Hibernate la resuelve sin
ir a la base.

**Medido sobre 200.000 tickets** (Postgres real, `EXPLAIN (ANALYZE, BUFFERS)`), la consulta
del historial de un empleado con 4.000 cargas:

| Variante | Plan | Buffers | Tiempo |
| --- | --- | --- | --- |
| Sin paginar | Bitmap Heap Scan + Sort de 4.000 filas | 2.074 | 3,848 ms |
| Paginada (`LIMIT 20`) | `Index Scan using tickets_persona_vigentes_idx` | **13** | **0,077 ms** |

**160 veces menos lecturas.** Y el dato que no era obvio: **sin paginar, el planner ignora
el índice nuevo.** Se probó explícitamente. O sea que el índice y la paginación no son dos
mejoras independientes que se puedan hacer por separado — son un paquete. Si se hubiera
shippeado solo el índice, habrían sido 6,4 MB sin usar.

**Cambio de contrato:** `/tickets/me` ahora devuelve `PagedModel` en vez de `List`, igual
que `/admin/tickets`. `mobile/services/tickets.ts` y la pantalla de historial se
actualizaron en el mismo cambio, con scroll incremental (`onEndReached`, 30 por página).

---

# ALTAS

## [x] DB-02 — Lombok `@Data` sobre relaciones bidireccionales: `StackOverflowError` latente

**Ubicación:**
- `backend/src/main/java/com/retrorental/backend/model/Empleado.java:16,24-26` (`jefe`)
- `backend/src/main/java/com/retrorental/backend/model/Administrador.java:14,16-25`
  (`empleados`, `vehiculos`)

**Descripción:** `Empleado` y `Administrador` usan `@Data`, que genera `toString()`,
`equals()` y `hashCode()` sobre **todos** los campos declarados. `Empleado.jefe` no está
excluido, y `Administrador.empleados`/`vehiculos` no tienen ninguna exclusión.

El ciclo se cierra solo: `Empleado.toString()` → llama a `jefe.toString()` (un
`Administrador`) → que incluye la lista `empleados` → que llama al `toString()` de cada
elemento → que vuelve a llamar a `jefe.toString()` → **recursión infinita →
`StackOverflowError`**. El mismo ciclo existe en `equals()` y en `hashCode()`.

**Por qué es alta y no crítica:** no explotó todavía. No hay ningún llamador actual que
invoque `toString()`/`equals()` sobre estas entidades (verificado con grep sobre llamadas
de logging). Pero está a **una sola línea de distancia** de explotar: un `log.debug` sobre
un empleado, una inspección en el debugger, un `assertEquals` sobre entidades en un test
futuro. Y cuando explote, va a explotar en el lugar menos esperado.

**Lo que más duele, otra vez:** el patrón correcto ya está en el repo.
`Vehiculo.operario` sí está excluido (`Vehiculo.java:94-95`). Se hizo bien una vez y no se
replicó.

**Mitigación aplicada:** `@ToString.Exclude @EqualsAndHashCode.Exclude` en
`Empleado.jefe`, `Administrador.empleados` y `Administrador.vehiculos`. Tres anotaciones.
En las colecciones del administrador además evita una `LazyInitializationException` si
alguien las toca fuera de una transacción abierta.

## [x] DB-03 — Índices faltantes en `tickets`, la tabla que más crece

**Ubicación:** `backend/src/main/resources/db/migration/V1__init_schema.sql:152-177`

**Descripción:** tres queries calientes sin índice que las soporte.

**a) `tickets.id_proveedor` no tiene índice.** Es una FK `NOT NULL` y `listForAdmin` filtra
por ella (`TicketService.java:581-582`). Todas las demás FKs de la tabla sí recibieron
índice en V1 — esta se pasó por alto.

**b) El historial del empleado no tiene índice compuesto.** Existen
`tickets_id_persona_idx` y `tickets_fecha_carga_idx` por separado, pero la query de DB-01
filtra por persona + `fecha_anulacion IS NULL` y ordena por `fecha_carga DESC`. Ojo con
esto: el índice parcial `tickets_fecha_anulacion_idx` de V7 indexa **solo las filas
anuladas** (`WHERE fecha_anulacion IS NOT NULL`), así que **no sirve** para esta query, que
busca justo lo contrario.

**c) El camino de escritura más caliente no tiene índice compuesto.**
`findByVehiculoIdAndUsoAcumuladoIsNotNullAndFechaAnulacionIsNullOrderByUsoAcumuladoAsc`
(`TicketRepository.java:30-31`) se llama en **cada** `create()` (línea 210) y en cada
`anular()` (líneas 283-284), para recalcular consumo y odómetro. Solo hay
`tickets_id_vehiculo_idx` de una columna.

**Mitigación aplicada** — `V10__agregar_indices_de_tickets.sql`, con **dos** de los tres
índices propuestos. Los cambios respecto de la recomendación original, y por qué:

**1. Sin `CONCURRENTLY`.** Producción tiene del orden de 3 tickets: un `CREATE INDEX` común
bloquea milisegundos. `CONCURRENTLY` obligaba a un `.conf` aparte con
`executeInTransaction=false` y puede dejar índices en estado `INVALID` si falla. Es
maquinaria que hoy no compra nada. Queda documentado en la migración que **cualquier índice
futuro** sobre `tickets` sí debe usarla, una vez que la tabla tenga volumen.

**2. El tercer índice (`tickets_vehiculo_consumo_idx`) NO se agregó.** Se probó y no se gana
el sueldo. Medido sobre 200.000 tickets:

| Variante | Buffers | Tiempo |
| --- | --- | --- |
| Con `tickets_id_vehiculo_idx` (lo que ya había) | 2.069 | 1,534 ms |
| Forzando el índice compuesto nuevo | 2.074 | 1,092 ms |

Con el índice viejo presente el planner **ni lo mira**, y forzándolo la mejora es marginal a
cambio de 4,5 MB. Habría sido peso muerto.

**Y esa medición destapó el problema real, que no era el índice.** Esa consulta trae
entidades `Ticket` **completas** cuando el cálculo solo necesita `(uso_acumulado, litros)`.
Mientras se traiga la entidad entera, Postgres tiene que ir al heap sí o sí y **ningún
índice lo evita**. Con una proyección + un índice cubridor:

| Variante | Plan | Buffers | Tiempo |
| --- | --- | --- | --- |
| Entidad completa (hoy) | Bitmap Heap Scan + Sort | 2.074 | 1,534 ms |
| Proyección + índice cubridor | `Index Only Scan`, `Heap Fetches: 0` | **16** | **0,190 ms** |

**130 veces menos lecturas, en el camino de escritura más caliente de la app** (corre en
cada carga de combustible). Los dos cambios tienen que viajar juntos: el índice sin la
proyección queda sin usar. Ver el nuevo ítem en la Fase 4.

## [ ] DB-04 — `litros` es `Double`/`double precision` y entra en cálculos de plata

**Ubicación:**
- `backend/src/main/java/com/retrorental/backend/model/Ticket.java:20-21`
- `backend/src/main/resources/db/migration/V1__init_schema.sql:154`
  (`litros double precision NOT NULL`)
- Consumidores: `StatsService.java:101-102`, `TicketService.java:601-604`,
  `ConsumoCalculator.java:87-90`

**Descripción:** el precio unitario es `BigDecimal` (bien), pero los litros son `Double` y
el gasto se calcula multiplicando uno por otro:
`precioUnitario.multiply(BigDecimal.valueOf(litros))`. El punto flotante binario no
representa exactamente la mayoría de los decimales, así que **el valor origen ya arrastra
error de representación antes de convertirse a `BigDecimal`**. Usar `BigDecimal` del otro
lado no salva nada si el operando entró contaminado.

Peor: `ConsumoCalculator.java:87-90` acumula `litros += ...` como `double` primitivo a lo
largo de muchos intervalos **antes** de envolver el total en `BigDecimal`. Ahí el error se
suma vuelta a vuelta.

Hay que ser justo: esto fue una decisión deliberada y está documentada
(`StatsService.java:33` explica que evita mezclar `Double` con `BigDecimal` en un `SUM` de
JPQL). El razonamiento se entiende. Pero el resultado es que **los números de gasto que ve
el jefe en el panel tienen deriva acumulada**, y en un sistema que existe para controlar
consumo de combustible, los números son el producto.

**Arreglo** — requiere migración de datos, no es un cambio cosmético. Verificar primero que
no haya pérdida de precisión sobre las filas existentes:

```sql
ALTER TABLE tickets ALTER COLUMN litros TYPE numeric(10,2) USING litros::numeric(10,2);
```

Y cambiar `Ticket.litros` a `BigDecimal`, propagando por `ConsumoCalculator`, `StatsService`
y los DTOs. Es el arreglo más invasivo de toda esta auditoría: merece su propia rama y su
propio ciclo de tests.

## [x] DB-05 — N+1 en el listado del padrón

**Ubicación:** `backend/src/main/java/com/retrorental/backend/service/HabilitadoService.java:126-130`
(`listAll`), mapeo en `:218-230`

**Descripción:** `findAllByOrderByFechaAltaDesc()` no trae la relación `persona`, y
`toResponse` lee `habilitado.getPersona().getUsername()` (líneas 219 y 228) por cada fila
que tenga la FK seteada. N+1 sobre el listado del padrón, que además tampoco pagina y crece
con cada alta.

**Mitigación aplicada:** `EmpleadoHabilitadoRepository.findAllConPersona()` con
`LEFT JOIN FETCH`. `LEFT` y no `INNER` a propósito: las filas del padrón **sin usar** tienen
`persona` en NULL y son justo las que el admin más necesita ver; un INNER las borraría del
listado. (Sí, el mismo error que DB-00 — por eso se puso explícito y comentado.)

**Pendiente:** la paginación de este listado. No entró en la Fase 1 porque el padrón crece
por altas manuales del admin, no por operación diaria como los tickets. Queda en Fase 4.

## [ ] TST-01 — Vacíos de cobertura en las tres clases con más lógica cruzada

**Ubicación:**
- `backend/src/main/java/com/retrorental/backend/controller/AdminHabilitadoController.java`
- `backend/src/main/java/com/retrorental/backend/service/VehiculoService.java`
- `backend/src/main/java/com/retrorental/backend/service/EmpleadoService.java`

**Descripción:** la suite existente es buena, pero tiene tres agujeros que no son
casualidad: son justo las clases con más lógica de estado cruzado.

**`AdminHabilitadoController` — cero tests.** Es el lado de escritura del padrón, o sea
**el control anti-abuso central de la aplicación** — lo dice el propio `HabilitadoServiceTest`
en sus comentarios, y es la mitigación que cerró SEC-01 en la auditoría de seguridad. Su
capa HTTP nunca se ejerció: ni el gate de rol, ni el tope de `MAX_BULK = 500` vía `@Size`
sobre `List<@Valid ...>` con `@Validated` — que es *exactamente* el caso donde Spring
silenciosamente no valida los elementos de la lista si falta una anotación. Ese es un
trampa conocida y acá no hay red.

**`VehiculoService` — cero tests unitarios.** Contiene la máquina de estados de asignación
de vehículos: `tomar()`/`liberar()`, con idempotencia si es el mismo operario, 409 si lo
tomó otro, 409 si no está `DISPONIBLE`, y desasignación en cascada al desactivar. Un bug
acá **asigna el mismo vehículo a dos empleados a la vez**.

**`EmpleadoService` — cero tests.** `desactivar()` cascadea liberando cada vehículo
asignado; `create()` tiene un efecto cruzado sobre el padrón (`marcarUsadoSiExiste`). Un
bug deja vehículos "fantasma" apuntando a un empleado dado de baja, o el padrón
desincronizado.

Hay más clases sin test (`UsernameGenerator`, `MinioStorageService`,
`MistralTicketAnalysisService`, `JwtUtil`, `FixedWindowCounter`, los dos validators, los
services triviales de `Precio`/`Proveedor`/`Persona`), pero esas tres son las que
justifican la severidad alta: **son las que pueden corromper datos en silencio.**

**Arreglo:**
1. `AdminHabilitadoControllerTest` espejando el patrón de `AdminHerramientaControllerTest`
   (401/403/200/400/409, más un caso de bulk por encima de 500).
2. `VehiculoServiceTest`: re-toma idempotente, conflicto por toma ajena, conflicto por
   estado no disponible, `liberar()` por quien no es el dueño, colisión de identificador.
3. `EmpleadoServiceTest`: cascada de desasignación y el efecto sobre el padrón.
4. Taggear las clases nuevas y agregar los dominios faltantes a `domain-tags.conf`
   (ver INF-02) — hoy esos dominios ni siquiera existen en el mapa.

---

# MEDIAS

## [ ] SVC-01 — `TicketService` es una god class de 759 líneas

**Ubicación:** `backend/src/main/java/com/retrorental/backend/service/TicketService.java`

**Descripción:** es el archivo más grande del backend **y** el segundo con más churn en el
historial de git (8 modificaciones). Esa combinación no es casualidad: los archivos que
hacen demasiado se tocan en cada feature, y cada vez que se tocan es una oportunidad de
romper algo no relacionado.

Mezcla seis responsabilidades:
1. CRUD de tickets
2. Resolución y reemplazo del catálogo de precios (`resolvePrecioPorId`,
   `resolvePrecioPorCombustible`, `reemplazarVigente`, `fueraDeMargen`)
3. Alta automática de proveedor/precio desde OCR (`resolveProveedor`, `resolvePrecio`,
   `matchProveedor`)
4. Recálculo de consumo y odómetro del vehículo (`recalcularConsumo`,
   `recalcularUsoAcumulado`)
5. Filtrado y paginación del admin (`listForAdmin`, con una lambda de Specification de 65
   líneas en `:564-613`)
6. Mapeo a DTO

`create()` sola tiene 113 líneas (`:72-185`).

**Arreglo:** extraer `PrecioResolutionService` (grupo 2) y `VehiculoConsumoService`
(grupo 4). Los dos son ya hoy métodos privados autocontenidos, sin estado oculto más allá
de sus parámetros — la extracción es mecánica y de bajo riesgo. Hacerlo *antes* de TX-01
hace TX-01 más fácil.

## [ ] SVC-02 — `StatsService` trae todo y filtra en memoria

**Ubicación:** `backend/src/main/java/com/retrorental/backend/service/StatsService.java:68-85`

**Descripción:** `statsForRange()` trae **todos** los tickets del rango de fechas vía
`findForStats()` —que hace `JOIN FETCH` de persona, proveedor, precio, vehículo y
herramienta— y recién después filtra por `vehiculoId`/`empleadoIds` en memoria (líneas
74-85). Para un rango mensual sobre una flota grande, hidrata muchísimas más filas y
asociaciones de las que el resultado necesita.

**Arreglo:** empujar `vehiculoId`/`empleadoIds` como predicados opcionales dentro de la
query, con el mismo patrón de Criteria API que ya usa `TicketService.listForAdmin`.

## [ ] DB-06 — `PrecioService.listVigentes()` trae la tabla entera y filtra en memoria

**Ubicación:** `backend/src/main/java/com/retrorental/backend/service/PrecioService.java:23-27`

**Descripción:** hace `precioRepository.findAll()` y filtra `fechaHasta == null` en Java.
Carga el **historial completo de precios**, una tabla que solo crece: `reemplazarVigente`
(`TicketService.java:481-496`) nunca borra las filas viejas, las cierra con `fechaHasta`.
Cada consulta del selector de precios paga el historial acumulado entero.

**Arreglo:** método derivado `findByFechaHastaIsNull()` más el índice parcial:
```sql
CREATE INDEX CONCURRENTLY precios_vigente_general_idx
    ON precios (fecha_hasta) WHERE fecha_hasta IS NULL;
```

## [ ] DB-07 — Carga masiva del padrón: 2N viajes a la base

**Ubicación:** `backend/src/main/java/com/retrorental/backend/service/HabilitadoService.java:154-167`

**Descripción:** `createBulk` recorre la lista haciendo `existsByDocumento` + `save` por
cada elemento. Con el tope de 500 del endpoint, eso son **hasta 1.000 round-trips** para
una operación cuyo propósito declarado es "cargar la nómina de una sola vez".

**Arreglo:** un `findByDocumentoIn(List<String>)` para traer los existentes, filtrar en
memoria contra ese set, y un `saveAll(...)` único.

## [ ] DB-08 — `EmpleadoService.desactivar()` hace N updates

**Ubicación:** `backend/src/main/java/com/retrorental/backend/service/EmpleadoService.java:113-119`

**Descripción:** recorre `empleado.getVehiculos()` llamando a `vehiculoRepository.save()`
por vehículo para poner `operario` en null.

Un matiz honesto: los `save()` explícitos son **redundantes**, no dañinos — el dirty
checking de JPA ya persiste el cambio al hacer flush, así que no generan queries de más.
Pero el flush sí emite un `UPDATE` por vehículo. Con un empleado que tenga muchos vehículos
asignados, es N sentencias donde alcanza una.

**Arreglo:**
```java
@Modifying
@Query("UPDATE Vehiculo v SET v.operario = NULL WHERE v.operario.id = :empleadoId")
void desasignarTodosDe(@Param("empleadoId") Long empleadoId);
```

## [ ] DB-09 — Sin sizing de Hikari ni batch size de Hibernate

**Ubicación:** `application.yml`, `application-dev.yml`, `application-prod.yml`

**Descripción:** ningún perfil configura `spring.datasource.hikari.*` ni
`spring.jpa.properties.hibernate.jdbc.batch_size`. El pool corre con el default de 10
conexiones **en producción también**, por decisión implícita y no por decisión tomada.

No es necesariamente incorrecto al volumen actual. Pero es el multiplicador que convierte a
TX-01 en crítico, y merece ser una decisión explícita y escrita en vez de un default
heredado en silencio.

**Arreglo:** fijar el tamaño del pool explícitamente por perfil, con un comentario que
explique el criterio. Agregar `batch_size` para cuando se apliquen los `saveAll` de DB-07.

## [ ] SVC-03 — `MinioConfig` sin timeouts explícitos

**Ubicación:** `backend/src/main/java/com/retrorental/backend/config/MinioConfig.java:20-37`

**Descripción:** los beans `minioClient()`/`minioPublicClient()` no configuran timeouts de
connect/read/write, a diferencia de `MistralConfig.java:27-30` que sí los configura
explícitamente. Un MinIO que no responde bloquea las llamadas de upload/getUrl/delete
**indefinidamente**. La asimetría entre los dos configs es la señal de que fue un olvido,
no una decisión.

**Arreglo:** construir el cliente con un `OkHttpClient` explícito (o
`MinioClient.Builder.httpClient(...)`) con timeouts, espejando `MistralConfig`. Es parte
del arreglo de TX-01.

## [ ] WEB-01 — Verbo HTTP incorrecto devuelve 500 en vez de 405

**Ubicación:** `backend/src/main/java/com/retrorental/backend/exception/GlobalExceptionHandler.java:170`
(`handleUnexpected`)

**Descripción:** no hay `@ExceptionHandler(HttpRequestMethodNotSupportedException.class)`.
Cuando un cliente pega a una ruta válida con el verbo equivocado (por ejemplo
`PATCH /tickets`), Spring lanza esa excepción, ningún handler específico la agarra, y cae
al catch-all: **devuelve 500 con "Ocurrio un error inesperado" y lo loguea como error de
servidor no manejado.**

Doble costo: el cliente móvil recibe un código que no describe el problema, y el log de
errores se contamina con falsas alarmas de "error inesperado" que no son errores del
servidor. Un log lleno de ruido es un log que nadie mira.

**Arreglo:** agregar el handler devolviendo 405 con su `ErrorCode` correspondiente.

## [ ] BLD-01 — Sin JaCoCo, sin chequeo de dependencias, sin análisis estático

**Ubicación:** `backend/pom.xml`

**Descripción:** el build no tiene plugin de cobertura (JaCoCo), ni analizador estático
(SpotBugs/Error Prone), ni chequeo de CVEs en dependencias (OWASP dependency-check), ni
formateador (Spotless).

Las consecuencias son concretas y no teóricas:
- **No hay forma de saber si los vacíos de TST-01 se están cerrando o agrandando.** Sin
  medición no hay mejora, hay opinión.
- **No hay señal automática de un CVE nuevo** en `minio`, `jjwt` o el driver de Postgres —
  tres dependencias con versión fijada a mano (ver BLD-02) en una app que maneja auth, JWT,
  subida de archivos y rate limiting.

**Arreglo:** agregar `jacoco-maven-plugin` atado a `verify` (aunque sea con un umbral
blando al principio, para tener la línea de base) y `org.owasp:dependency-check-maven`. El
proyecto tiene pocas dependencias, así que el scan no va a pesar en el build.

## [ ] INF-01 — `dockerfile` sin `HEALTHCHECK`

**Ubicación:** `backend/dockerfile:1-26`

**Descripción:** no hay instrucción `HEALTHCHECK`. El orquestador no tiene forma de
distinguir "el proceso está vivo" de "la aplicación está sirviendo". Combinado con el nginx
de `infra/` como reverse proxy, **un backend colgado pero con el proceso vivo sigue
recibiendo tráfico**.

**Arreglo:** la app ya expone `/health` (confirmado por `HealthControllerTest`), así que es
una línea:
```dockerfile
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://localhost:8080/health || exit 1
```

## [ ] INF-02 — `domain-tags.conf` no mapea `empleado`, `precio` ni `proveedor`

**Ubicación:** `backend/scripts/domain-tags.conf`

**Descripción:** no hay entrada para esos tres dominios. Cualquier cambio en
`AdminEmpleadoController`, `EmpleadoService`, `PrecioController`, `PrecioService`,
`ProveedorController` o `ProveedorService` no matchea ningún patrón, así que
`test-affected.sh` cae a la suite completa.

El fallback es **seguro** (así está diseñado, y está bien), pero significa que la
optimización nunca aplica para esos dominios — que son justo los que TST-01 marca como sin
tests. El script está optimizando todo menos donde más falta hace.

**Arreglo:** agregar los tres mapeos. Cuidado con el de `precio`: la cadena `Precio`
aparece también dentro de `TicketService` y `ConsumoCalculator`, así que hay que verificar
que el regex no genere colisiones de tag falsas. Y agregar los `@Tag` correspondientes
cuando se escriban los tests de TST-01.

## [ ] SVC-04 — El validador de origen de carga reporta un solo error por request

**Ubicación:** `backend/src/main/java/com/retrorental/backend/validation/OrigenCargaCoherenteValidator.java:23-69`

**Descripción:** las ocho reglas están encadenadas como `if / else if`, así que solo se
reporta la **primera** violación. Un payload de herramienta al que le falte
`tipoCombustible` **y** que además mande `usoAcumulado` indebidamente solo devuelve el
error de `usoAcumulado` (rama de línea 46). El empleado corrige eso, reenvía, y recién ahí
descubre el segundo problema.

Cada rama es individualmente correcta — no es un bug de correctitud. Es que el usuario paga
un viaje de ida y vuelta por cada error que tenga.

**Arreglo:** acumular las violaciones en vez de cortar en la primera, o —si la cadena es
deliberada por algún motivo de precedencia entre reglas— dejarlo documentado con un
comentario para que el próximo que lo lea no lo "arregle" rompiendo la precedencia.

---

# BAJAS

## [ ] DB-10 — Código muerto: 6 métodos de repositorio y 1 DTO sin uso

**Ubicación:**
- `backend/src/main/java/com/retrorental/backend/repository/TicketRepository.java:33`
  (`findByPersonaIdAndFechaAnulacionIsNull`)
- `.../TicketRepository.java:36` (`findByProveedorIdAndFechaAnulacionIsNull`)
- `.../TicketRepository.java:37-38` (`findByFechaCargaBetweenAndFechaAnulacionIsNull`)
- `backend/src/main/java/com/retrorental/backend/repository/ProveedorRepository.java:13`
  (`existsByCuit`)
- `backend/src/main/java/com/retrorental/backend/repository/PrecioRepository.java:15`
  (`findByServicioAndFechaHastaIsNull`)
- `backend/src/main/java/com/retrorental/backend/dto/response/FileUploadResponse.java:3`

**Descripción:** cero referencias fuera de su propia declaración, incluyendo tests
(verificado con grep por cada símbolo). El primero está superseded por la variante
`...OrderByFechaCargaDesc` que sí se usa en `listMine`.

Ojo con un detalle: `findByProveedorIdAndFechaAnulacionIsNull` claramente se escribió para
el filtro por proveedor de `listForAdmin`, que terminó resolviéndose con un predicado
ad-hoc en la Specification — y sin índice (ver DB-03a). Vale decidir si se borra o si se
usa.

**Arreglo:** borrar los seis, salvo que se opte por lo contrario en el caso del proveedor.

## [ ] DB-11 — `@Data` en el resto de las entidades: `equals`/`hashCode` sobre campos mutables

**Ubicación:** `Persona`, `Vehiculo`, `Precio`, `Proveedor`, `Herramienta`,
`EmpleadoHabilitado`

**Descripción:** aparte del ciclo de DB-02, `@Data` genera `equals`/`hashCode` sobre
**todos los campos mutables**, no sobre el `id`. `Ticket` es la única entidad que usa
`@Getter`/`@Setter` sin generar métodos de identidad — o sea, la única que quedó bien.

Hoy no está roto: no hay ningún `Set<Entidad>` en el código (verificado con grep). Pero es
frágil: el día que alguien meta entidades en un `Set` o en un `HashMap`, mutar un campo
después de insertarlas hace que el elemento se vuelva irrecuperable de la colección.

**Arreglo:** `equals`/`hashCode` basados en `id` (el patrón canónico de Hibernate) en todas
las entidades, por consistencia con `Ticket`. No es urgente dado el uso actual.

## [ ] WEB-02 — `AuthResponse` es la única respuesta mutable

**Ubicación:** `backend/src/main/java/com/retrorental/backend/dto/response/AuthResponse.java:7-9`

**Descripción:** es una clase Lombok `@Data @AllArgsConstructor`, mientras que los otros 18
DTOs de respuesta son `record` inmutables. Se construye una sola vez
(`AuthService.java:135`) y se devuelve: no hay ninguna necesidad de mutabilidad.

**Arreglo:** convertir a `record`.

(Aclaración para que nadie lo "arregle" de más: los DTOs de **request** sí son clases
`@Data` mutables a propósito. `CreateTicketRequest` se bindea con `@ModelAttribute` para
multipart, que necesita setters — un record no funcionaría ahí.)

## [ ] WEB-03 — Los endpoints de creación devuelven 200 en vez de 201

**Ubicación:** `AdminEmpleadoController.java:43`, `AdminHerramientaController.java:44`,
`AdminVehiculoController.java:44`, `AuthController.java:21`

**Descripción:** todos los `@PostMapping` de creación devuelven `ResponseEntity.ok(...)`.
Es **consistente en toda la capa** —no es un descuido puntual— así que se lee como estilo
de la casa, pero se aparta de la convención REST y omite el header `Location`.

**Arreglo:** o se pasan todos a 201, o se documenta la convención explícitamente para que
en la próxima review nadie lo reporte como bug. Lo que no conviene es dejarlo ambiguo.

## [ ] SVC-05 — `normalizar()` duplicado

**Ubicación:** `backend/src/main/java/com/retrorental/backend/service/VehiculoService.java:218-224`
y `backend/src/main/java/com/retrorental/backend/service/HerramientaService.java:81-87`

**Descripción:** método privado idéntico (trim, null si queda vacío).

Importante: `HabilitadoService.normalizar` y `UsernameGenerator.normalizar` **no** son
duplicados —hacen quitado de acentos para propósitos distintos— y están bien separados. No
unificar esos.

**Arreglo:** extraer un helper compartido solo para los dos casos idénticos.

## [ ] SVC-06 — `StatsService.consumoDelPeriodo` dispara un SELECT lazy evitable

**Ubicación:** `backend/src/main/java/com/retrorental/backend/service/StatsService.java:224`

**Descripción:** `cargasDelVehiculo.get(0).getVehiculo()` dispara una query extra. No está
en un loop, así que el impacto es mínimo, pero el `vehiculoId` ya lo conoce quien llama.

## [ ] BLD-02 — Elementos vacíos en el `pom.xml`

**Ubicación:** `backend/pom.xml:14-28`

**Descripción:** `<name/>`, `<description/>`, `<url/>`, `<licenses><license/></licenses>`,
`<developers><developer/></developers>` y `<scm>` están presentes pero vacíos. Un
`<license/>` vacío es **peor que no tenerlo**: una herramienta de scanning de licencias lo
reporta como "licencia desconocida" en vez de omitirlo.

**Arreglo:** completarlos o borrar los bloques. Los stubs vacíos no sirven a nadie.

(Verificado y descartado como problema: las versiones fijadas a mano de `jjwt`,
`dotenv-java` y `minio` no las gestiona el BOM del parent de Spring Boot, así que fijarlas
es correcto y no evitable. Y `dotenv-java` **sí se usa**, en
`BackendApplication.java:3,11,14`.)

## [ ] INF-03 — Flags de JVM para contenedor

**Ubicación:** `backend/dockerfile:14,26`

**Descripción:** no se setea `-XX:MaxRAMPercentage`. La JVM usa el default del 25% de la
memoria del contenedor para el heap, que es conservador y puede desaprovechar un contenedor
ajustado.

**Arreglo:** `ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]`, si el
límite de memoria en `infra/` está ajustado.

## [ ] INF-04 — Imágenes base pineadas por tag, no por digest

**Ubicación:** `backend/dockerfile:2,14`

**Descripción:** `maven:3.9-eclipse-temurin-21` y `eclipse-temurin:21-jre` pueden ser
repunteadas upstream, lo que hace que el build no sea reproducible bit a bit.

**Arreglo:** decisión de equipo, no hay una respuesta única. Pinear por digest da
reproducibilidad; dejarlo flotante da parches de seguridad automáticos. Muchos equipos
eligen lo segundo a propósito. Lo importante es que sea una elección y no un default.

## [ ] TST-02 — Testcontainers sin `withReuse`

**Ubicación:** `backend/src/test/java/com/retrorental/backend/TestcontainersConfiguration.java:26-27`

**Descripción:** el contenedor de Postgres paga arranque completo en cada corrida de JVM.
Está muy mitigado porque `BackendApplicationTests` es la **única** clase que usa
Testcontainers (todo lo demás son slices), así que el costo se paga una vez por corrida, no
por clase.

**Arreglo:** opcional. `.withReuse(true)` más `testcontainers.reuse.enable=true` en
`~/.testcontainers.properties` si molesta en iteración local. En CI no aporta: los
contenedores son efímeros igual.

## [ ] TST-03 — Convención de estado compartido en tests de rate limit sin documentar

**Ubicación:** `AnalyzeRateLimitFilterTest.java`, `LoginRateLimitFilterTest.java`

**Descripción:** estos tests dependen de que el estado del filtro es un singleton del
contexto de Spring compartido entre métodos de la clase. **Lo mitigan correctamente** (IP y
username únicos por test, y está comentado). No hay bug.

Se anota solo porque es el único lugar donde el estado mutable compartido podría filtrarse
entre tests si un autor futuro no replica el patrón.

**Arreglo:** dejar la convención escrita en `backend/AGENTS.md`, junto al resto de las
reglas de testing.

## [ ] TST-04 — Sobrecarga confusa en `TicketServiceTest`

**Ubicación:** `backend/src/test/java/com/retrorental/backend/controller/TicketServiceTest.java:439-446,536`

**Descripción:** dos helpers privados se llaman ambos `ticketDe` (sobrecargados por firma:
`ticketDe(int, int, double)` y `ticketDe(int, Persona)`). Es Java válido, pero en un archivo
de 716 líneas se lee como intención duplicada.

**Arreglo:** renombrar a `ticketConLectura` y `ticketDeDueno`. Cosmético.

---

# Plan de acción sugerido

El orden importa: algunos arreglos hacen más fáciles a los otros.

## ~~Fase 1 — Frenar la hemorragia de escalabilidad~~ ✅ COMPLETADA

Rama `perf/fase-1-indices-y-fetch-joins`. Suite en 207/207 (204 previos + 3 nuevos).

1. ~~**DB-03** (índices)~~ — V10, con dos índices en vez de tres y sin `CONCURRENTLY`. El
   tercero se descartó por medición, no por criterio: no se ganaba el sueldo.
2. ~~**DB-01** (fetch join + paginación en `/tickets/me`)~~ — incluye el cambio en
   `mobile/` (scroll incremental). 160x medido.
3. ~~**DB-05** (fetch join del padrón)~~
4. ~~**DB-02** (exclusiones de Lombok)~~
5. ~~**DB-00** (bug del INNER JOIN)~~ — no estaba planificado: apareció al abrir
   `listForAdmin` para copiarle el patrón de paginación.

**Lo que dejó esta fase además del código:** infraestructura de tests de persistencia
(`TicketPersistenciaTest`, `@SpringBootTest` contra Postgres real). Hasta ahora el único
test con base de verdad era el `contextLoads`. Los bugs tipo DB-00 son invisibles para un
`@WebMvcTest` con el service mockeado, porque viven en el SQL que Hibernate genera y que ese
test nunca ejecuta. Ahora hay dónde poner ese tipo de test.

**Pendiente de coordinación:** `/tickets/me` cambió de `List` a `PagedModel`. **El APK
instalado deja de leer bien el historial hasta que se actualice.** Hay que sacar versión
nueva junto con este deploy.

## Fase 2 — Sacar el I/O de las transacciones

5. **SVC-01** (extraer `PrecioResolutionService` y `VehiculoConsumoService` de
   `TicketService`) — hacerlo **primero** deja `create()` y `analyze()` mucho más chicos, y
   por lo tanto hace el paso siguiente mucho más seguro de revisar.
6. **TX-01** (I/O fuera de transacción + timeouts de MinIO + sizing de Hikari, los tres
   juntos) — es el hallazgo crítico de disponibilidad.

## Fase 3 — Red de seguridad antes de seguir tocando

Esta fase no agrega ninguna funcionalidad, y por eso es la que se saltea siempre. No la
saltees. Las dos fases anteriores acaban de mover código en `TicketService`, `VehiculoService`
y `EmpleadoService`, que son **exactamente** las clases sin tests.

7. **TST-01** (tests de `AdminHabilitadoController`, `VehiculoService`, `EmpleadoService`)
8. **INF-02** (mapear los dominios faltantes en `domain-tags.conf`, ya con los tags nuevos)
9. **BLD-01** (JaCoCo + OWASP, para tener línea de base medible de acá en adelante)

## Fase 4 — Eficiencia y limpieza

10. **Proyección + índice cubridor en el recálculo de consumo** (ver DB-03). Medido: de
    2.074 buffers y 1,53 ms a 16 buffers y 0,19 ms, en el camino de escritura que corre en
    cada carga de combustible. Es el mejor retorno que queda sobre la mesa. Requiere cambiar
    `TicketRepository` a una proyección `(uso_acumulado, litros)` y adaptar
    `ConsumoCalculator`, más la migración con el índice `INCLUDE (litros)`.
11. **SVC-02**, **DB-06**, **DB-07**, **DB-08** (filtrar en la base, no en memoria)
12. **Paginar el listado del padrón** (resto de DB-05)
13. **DB-10** (borrar el código muerto)
14. **WEB-01**, **INF-01** (405 correcto, healthcheck)
15. El resto de las bajas, por goteo.

## Fase 5 — Aparte, con su propia rama

14. **DB-04** (`litros` a `BigDecimal`) — es el único cambio que toca schema, entidad,
    servicios, DTOs y el contrato con `mobile/` de una sola vez. Merece su propia rama, su
    propio ciclo de tests y su propia ventana de despliegue. No lo metas de arrastre en
    otro PR.

---

# Nota final sobre este backend

Vale decirlo derecho: **este código está bastante mejor de lo que suele estar.** La capa web
es ejemplar, los renames de schema se hicieron sin dejar una sola columna zombie, el bulkhead
del OCR está bien pensado, los tests que existen explican *por qué* prueban lo que prueban, y
el `dockerfile` hace bien las cuatro cosas que casi nadie hace bien.

Hay un patrón que se repite en los hallazgos más graves y que vale la pena nombrar, porque es
el aprendizaje real de esta auditoría: **casi siempre la solución correcta ya existía en el
mismo archivo y no se replicó.**

- `listForAdmin` pagina y hace fetch join; `listMine` no hace ninguna de las dos (DB-01).
- `Vehiculo.operario` excluye del `toString`; `Empleado.jefe` no (DB-02).
- `MistralConfig` configura timeouts; `MinioConfig` no (SVC-03).
- Todas las FKs de `tickets` tienen índice; `id_proveedor` no (DB-03).
- `findForStats` usa `LEFT JOIN FETCH` **y explica en un comentario por qué el INNER
  rompería**; `listForAdmin` usa INNER igual (DB-00).

Ese último es el caso más elocuente, y por eso vale cerrar con él. No es que no supieran: lo
sabían tan bien que lo dejaron escrito. Y aun así, en el archivo de al lado, el error está.
Escribir la advertencia no alcanza si nadie la busca cuando escribe el código nuevo.

No es un problema de conocimiento —el equipo sabe hacerlo, la evidencia está en el repo— es
un problema de consistencia al replicar. Eso se arregla con checklist y con revisión, no con
más estudio. Cuando arregles cada uno de estos, la pregunta que vale es: *"¿dónde más hice
esto mismo?"*

Y una advertencia sobre este mismo documento, porque también es la lección de la Fase 1:
**una auditoría estática encontró 29 hallazgos y se le pasó el único que ya estaba
rompiendo datos en producción** (DB-00). Apareció recién al abrir el archivo para tocarlo,
y se confirmó con datos reales en una base real. Leer código encuentra mucho; ejecutarlo
contra Postgres con volumen encuentra lo otro. Las dos cosas hacen falta, y este documento
solo hizo la primera.
