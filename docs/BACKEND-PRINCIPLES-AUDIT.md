# Auditoría de principios (SOLID / DRY / YAGNI) — Backend

Fecha de la auditoría: 2026-08-20
Alcance: `backend/src/main/java` completo — 113 archivos Java, ~7.400 líneas.
Se leyeron entero: las 13 capas de controller, los 13 servicios, las 7
entidades JPA (+2 enums complejos y el embeddable `Telefono`), los 7
repositorios, la capa de excepciones (`AppException` + 6 subclases +
`ErrorCode` + `GlobalExceptionHandler`), los 6 archivos de `security/`, los 5
de `config/` y los 6 de `validation/` (dos `@Constraint` a nivel de clase con
sus validators). No se leyeron con el mismo detalle los 31 archivos de test ni
las migraciones Flyway fila por fila — no son objeto de esta auditoría.

**Este documento cubre exclusivamente SOLID, DRY, YAGNI y separación de
responsabilidades.** Lo que NO cubre, a propósito:

- **Seguridad** (auth, validación de input, hashing, headers) → ya está en
  `docs/SECURITY-AUDIT.md`.
- **Performance de queries, N+1, índices, transacciones largas** → ya está en
  `docs/BACKEND-AUDIT.md` (DB-01 a DB-11, TX-01, SVC-01 a SVC-06). Varios de
  esos hallazgos ya mencionan refactors de responsabilidad única (la extracción
  de `TicketService` en tres servicios, por ejemplo) — acá no se repiten, solo
  se referencian donde hace falta contexto.
- **Cobertura de tests, código muerto, build/infra** → también en
  `docs/BACKEND-AUDIT.md` (TST-01, DB-10, BLD-01).

Donde un hallazgo de esos documentos tiene una lectura de diseño (por ejemplo,
DB-11 sobre `@Data` y `equals`/`hashCode`), se referencia en vez de duplicarse.

## Estado general

| Severidad | Total | Resueltas | Parciales | Pendientes |
| --- | --- | --- | --- | --- |
| Crítica | 0 | 0 | 0 | 0 |
| Alta | 1 | 0 | 0 | 1 |
| Media | 1 | 0 | 0 | 1 |
| Baja | 3 | 0 | 0 | 3 |

## Contexto de escala — leer antes de priorizar

Mismo criterio que el resto de las auditorías de este repo: la producción hoy
es una sola empresa con un puñado de vehículos. Nada de lo que sigue está
"roto" en el sentido de un bug visible hoy. Lo que sigue son decisiones de
diseño que **funcionan bien al tamaño actual y se vuelven más caras de
sostener a medida que el dominio crece** — más roles, más entidades con ABM
parecido, más formularios que validan lo mismo. Leer "Alta" como "esto se
paga caro el día que el negocio agrega la próxima pieza de complejidad
esperable", no como "esto está fallando ahora".

## Lo que ya está bien (no tocar)

La auditoría anterior (`docs/BACKEND-AUDIT.md`) ya dejó el backend en un
estado bastante prolijo — varios de sus hallazgos fueron directamente
refactors de responsabilidad única. Esto es lo que se verificó leyendo el
código actual, no la documentación:

**Separación de capas, sin excepciones.** Los 13 controllers son delegación
pura: ningún controller arma una entidad, arma una `Specification` o toca un
repositorio. `TicketController.create` es literalmente una línea. La regla de
autorización por rol tampoco se repite: vive una sola vez en
`SecurityConfig.filterChain` (`/admin/**` → `hasRole("ADMINISTRADOR")`) y cada
controller bajo `/admin` confía en eso en vez de volver a declararlo — así
quedó documentado con un comentario en cada uno.

**Inversión de dependencias aplicada donde rinde, no en todos lados.**
`StorageService` (interfaz) / `MinioStorageService` (única implementación) y
`TicketAnalysisService` / `MistralTicketAnalysisService` son las dos
interfaces con un solo implementador de todo el backend, y las dos tienen una
razón concreta: la segunda se instancia condicionalmente
(`@ConditionalOnProperty`, la app arranca sin API key de Mistral) y se inyecta
con `ObjectProvider` para que `TicketService.create()` no dependa de que exista;
las dos se mockean en los tests de `TicketService`. No hay interfaces "por las
dudas" en el resto del código — ningún otro servicio tiene una interfaz sin
una segunda implementación real o potencial.

**`TicketService` dejó de ser una god class.** La extracción en
`PrecioCatalogoService`, `VehiculoConsumoService` y `CatalogoOcrResolver`
(documentada en `docs/BACKEND-AUDIT.md`, SVC-01) es SRP bien aplicado: cada
uno tiene un invariante propio y escrito (el de `PrecioCatalogoService` — "hay
a lo sumo un precio vigente por proveedor+combustible" — está en su propio
Javadoc de clase). `TicketService` quedó en 496 líneas y orquesta, no
implementa las reglas del catálogo ni del consumo.

**`TipoVehiculo` centraliza las reglas que dependen del tipo.** El formato del
identificador (patente vs. número interno), si exige modelo y en qué unidad se
mide el uso están en el enum, no repartidos entre `IdentificadorCoherenteValidator`,
los DTOs y los services. El propio Javadoc del enum explica por qué: "si
estuviera como `@Pattern` en los DTOs habría que repetirla en el alta y en la
edición, y ninguna de las dos podría mirar el tipo para decidir". Es el patrón
correcto — y por eso llama la atención que `CreateVehiculoRequest` y
`UpdateVehiculoRequest` sí terminen duplicados en todo lo demás (ver BE-002).

**`fueraDeMargen`/`validarMargen` en `PrecioCatalogoService` es DRY de
manual.** Una sola regla ("¿el precio corregido se aleja demasiado del
vigente?") la comparten dos caminos que no tienen nada más en común: la
corrección manual del empleado y la resolución automática del OCR. Está
escrita una vez y los dos caminos la llaman.

**`StatsService.daily/weekly/monthly`** son tres líneas cada uno que solo
calculan los límites del rango y delegan en `statsForRange`, que tiene toda la
lógica. Es el ejemplo de libro de "varía un parámetro, no dupliques el
método".

**`FixedWindowCounter`** existe como clase aparte por una razón que el propio
código explica: hay dos rate limiters en la API (login por IP, análisis OCR
por usuario) con la misma lógica de ventana fija, y duplicarla habría dejado
dos copias de un control de seguridad para mantener sincronizadas. Es DRY
aplicado a algo que además es sensible a bugs — la elección correcta.

**`ConsumoCalculator`** es una utility class `final`, sin estado, sin
dependencias inyectadas, con un solo método público. No hace más de lo que su
nombre promete — YAGNI bien resuelto, cero abstracción de más para un cálculo
matemático que no necesita polimorfismo ni configuración.

## Patrones evaluados y descartados explícitamente

Dos candidatos a "violación" que se revisaron y se decidió **no** convertir en
hallazgo, para que quede escrito el motivo y nadie los "corrija" de más:

**CRUD parecido entre `EmpleadoService`, `VehiculoService`, `HerramientaService`
y `HabilitadoService`.** Las cuatro tienen forma similar (`listAll`, `create`
con chequeo de duplicado, `update`, baja lógica idempotente con 409). Se
evaluó si valía la pena un `AbstractCrudService<T, ID, CreateReq, UpdateReq,
Response>` genérico. **No vale la pena.** Las cuatro divergen en lo que
importa: `VehiculoService` tiene una máquina de estados completa
(`tomar`/`liberar`, con Estado y operario) que las otras tres no tienen;
`EmpleadoService.desactivar()` cascadea liberando vehículos, algo que no
aplica a herramientas; `HabilitadoService` no tiene `update` y tiene un
`createBulk` que no existe en ningún otro. Una clase base que absorbiera esto
necesitaría tantos hooks y method overrides que el resultado sería más código
y más indirección que las cuatro clases actuales, cada una de 30-60 líneas y
legible de punta a punta. Es exactamente el caso que YAGNI advierte: abstraer
una similitud estructural que no esconde una regla de negocio compartida.

**Interfaces para servicios de un solo implementador** (`EmpleadoService`,
`VehiculoService`, etc.). Ningún test los mockea vía interfaz — Mockito mockea
la clase concreta directamente y funciona perfecto. Agregar una interfaz acá
sería ceremonia sin un segundo implementador real ni a la vista, a diferencia
de `StorageService`/`TicketAnalysisService` (ver arriba), donde la interfaz sí
paga su costo.

---

# ALTAS

## [ ] BE-001 — El rol de una `Persona` vive en dos lugares que nada mantiene sincronizados

**Ubicación:**
- `backend/src/main/java/com/retrorental/backend/model/Persona.java:37-39` (campo `rol`, `Enumerated(STRING)`)
- `backend/src/main/java/com/retrorental/backend/model/Empleado.java:16` (`extends Persona`, herencia JOINED)
- `backend/src/main/java/com/retrorental/backend/model/Administrador.java:15` (`extends Persona`, herencia JOINED)
- Chequeos por **tipo** (`instanceof Empleado`): `TicketService.java:130`,
  `VehiculoService.java:140` y `:252`, `EmpleadoService.java:128`,
  `AuthService.java:131`, `JwtFilter.java:107`
- Chequeos por **enum** (`getRol()`): `TicketService.java:385`,
  `AuthService.java:105`, `:136`, `:151`, `PersonaService.java:29`,
  `JwtFilter.java:85`

**Descripción:** `Persona` usa herencia JPA `JOINED` (`Empleado` y
`Administrador` son subclases con tabla propia) **y además** guarda un campo
`rol` (`Rol.EMPLEADO` / `Rol.ADMINISTRADOR`) en la tabla base. Son dos
mecanismos distintos codificando el mismo hecho — "¿esta persona es empleado o
administrador?" — y **nada en el modelo de datos ni en el código obliga a que
coincidan**. Hoy coinciden porque cada camino de alta los setea a mano en el
mismo método (`AuthService.register`, `EmpleadoService.create`), pero eso es
disciplina de quien escribe cada alta nueva, no una invariante que el
compilador o la base verifiquen.

La consecuencia concreta es que el código de autorización está partido en dos
idiomas que conviven sin que ninguno "gane": la mitad de los chequeos son
`instanceof Empleado` (polimorfismo por tipo), la otra mitad son
`persona.getRol() == Rol.ADMINISTRADOR` (dato). `TicketService.puedeVer`
usa el segundo; `VehiculoService.resolveEmpleado` usa el primero para la
*misma pregunta* en el mismo dominio (quién puede operar sobre un vehículo).

**Por qué es Alta y no Media:** el criterio de esta auditoría es "¿esto se
rompe cuando el negocio crece?", y este es un caso de manual. La empresa tiene
hoy dos roles. El próximo paso natural de un sistema de gestión de flota que
crece es un tercer nivel — un "Encargado" o "Supervisor" que ve varios
empleados pero no es el administrador general. El día que eso pase, alguien va
a tener que decidir: ¿es una tercera subclase de `Persona`? ¿O es un
`Empleado` con `Rol.SUPERVISOR`? Si se elige lo segundo (el camino más rápido,
porque no toca el schema de herencia), **los seis `instanceof Empleado`
dispersos en cuatro archivos van a tratarlo como un empleado común** —
`VehiculoService.resolveEmpleado`, que decide quién puede tomar/liberar
vehículos, ni se entera de que hay un rol intermedio— mientras que los
chequeos por `getRol()` sí lo verían. Bug de autorización silencioso,
exactamente el tipo que no aparece en un test hasta que alguien lo pisa en
producción.

**¿Vale la pena arreglarlo?** Sí, pero no con urgencia de "antes del próximo
deploy" — con urgencia de "antes de que se agregue el próximo rol". Mientras
el dominio tenga exactamente dos roles y las dos altas sigan seteando ambos a
mano, el riesgo es teórico. El costo de posponerlo es bajo *siempre que se
resuelva antes* de tocar el modelo de roles, no después.

**Costo actual de implementación:** medio. No es una migración de datos (el
valor de `rol` ya es correcto en todas las filas existentes), pero toca cuatro
archivos de lectura (los `instanceof`) y requiere decidir si el campo `rol`
sigue persistido o pasa a derivarse del tipo.

**Cómo se integraría** — dos niveles, según cuánto se quiera invertir ahora:

1. **Mitigación barata (sin migración):** dejar el campo `rol` como está, pero
   agregar un método `esAdministrador()`/`esEmpleado()` en `Persona` que sea
   la ÚNICA fuente de verdad para la pregunta, implementado contra el tipo
   (`this instanceof Administrador`) y no contra el campo. Reemplazar los seis
   usos de `getRol() == Rol.ADMINISTRADOR` por esos métodos. Esto no arregla
   la duplicación de fondo, pero colapsa los dos idiomas en uno solo (el tipo,
   que es la fuente más difícil de desincronizar porque Hibernate la resuelve
   por la tabla, no por un valor que alguien puede olvidar setear).
2. **Arreglo de fondo:** convertir `rol` en un método abstracto de `Persona`
   (`public abstract Rol getRol();`), implementado en cada subclase devolviendo
   una constante (`Empleado.getRol() { return Rol.EMPLEADO; }`). Deja de ser
   una columna: se borra de la tabla `personas` con una migración Flyway. A
   partir de ahí es **imposible** que un `Empleado` tenga `Rol.ADMINISTRADOR`
   guardado — el tipo y el rol son la misma cosa, no dos que coinciden por
   convención. `JwtUtil.generateToken` y el resto de los usos de `getRol()`
   siguen funcionando sin cambios porque la firma del método no cambia.

---

# MEDIAS

## [ ] BE-002 — `CreateVehiculoRequest` y `UpdateVehiculoRequest` son el mismo DTO copiado y pegado

**Ubicación:**
- `backend/src/main/java/com/retrorental/backend/dto/request/CreateVehiculoRequest.java:27-65`
- `backend/src/main/java/com/retrorental/backend/dto/request/UpdateVehiculoRequest.java:26-67`

**Descripción:** los ocho campos (`identificador`, `modelo`, `tipoVehiculo`,
`tipoCombustible`, `capacidadTanque`, `estado`, `fechaUltimoMantenimiento`,
`usoAcumulado`, `consumoPromedio` — son nueve, en realidad) están declarados
dos veces con las mismas anotaciones de Bean Validation, los mismos mensajes
de error, en el mismo orden. La única diferencia real es `estado`: opcional
en el alta (`Estado estado;` sin `@NotNull`, porque "si no viene, se crea
DISPONIBLE") y obligatorio en la edición (`@NotNull`). Los comentarios que
explican `@IdentificadorCoherente` están copiados palabra por palabra en los
dos archivos.

Es la misma situación que el propio `TipoVehiculo` señala en su Javadoc como
el motivo para centralizar las reglas de formato en el enum — "si estuviera
en los DTOs habría que repetirla en el alta y en la edición". Acá pasó con
todo lo demás: dos formularios que representan "los datos gestionables de un
vehículo" en las dos operaciones que los editan completos (`PUT` reemplaza
todo), sin ningún tipo que capture ese concepto una sola vez.

**Por qué es Media:** ya hay evidencia de que la duplicación **no se mantiene
sincronizada perfectamente** — la diferencia de `estado` es intencional y está
documentada, pero es exactamente el tipo de divergencia que, sin comentario,
sería indistinguible de un olvido. El día que se agregue una validación nueva
(por ejemplo, un rango para `consumoPromedio`), hay que acordarse de tocar los
dos archivos; olvidar uno no rompe la compilación ni los tests existentes,
solo deja el alta y la edición validando distinto sin que nadie lo note hasta
que un usuario lo prueba.

**¿Vale la pena arreglarlo?** Sí, y es barato — no es un caso de abstracción
prematura porque ya hay DOS usos reales (no uno especulativo) con el 90% de
los campos idénticos.

**Costo actual de implementación:** bajo. Nueve campos, sin lógica.

**Cómo se integraría:** extraer una clase base `VehiculoDatosGestionables`
(no abstracta si Lombok `@Data` complica la herencia; alcanza con una clase
concreta con `@Data`) con los ocho campos comunes y sus validaciones,
implementando `VehiculoIdentificable` (que ya existe para este propósito, ver
`IdentificadorCoherenteValidator`). `CreateVehiculoRequest` la extiende y
agrega `estado` sin `@NotNull`; `UpdateVehiculoRequest` la extiende y
sobrescribe `estado` con `@NotNull` (o, más simple, mueve `estado` fuera de la
base y lo declara en cada subclase, que es exactamente el único campo que
diverge). `@IdentificadorCoherente` sigue funcionando igual porque valida
contra la interfaz, no contra la clase concreta.

---

# BAJAS

## [ ] BE-003 — `RegisterRequest` y `CreateEmpleadoRequest` repiten los mismos cuatro campos

**Ubicación:**
- `backend/src/main/java/com/retrorental/backend/dto/request/RegisterRequest.java:11-36`
- `backend/src/main/java/com/retrorental/backend/dto/request/CreateEmpleadoRequest.java:15-36`

**Descripción:** `nombre`, `apellido`, `documento` y `password` están
declarados con las mismas cuatro anotaciones y los mismos mensajes en los dos
DTOs — el propio Javadoc de `CreateEmpleadoRequest` lo dice: "Igual que
RegisterRequest pero sin rol". Es una duplicación menor (cuatro campos
primitivos con validación estable: nombre/apellido no van a cambiar de
regla) pero es la misma familia de problema que BE-002 a menor escala.

**¿Vale la pena arreglarlo?** Marginal. A diferencia de BE-002, acá los dos
DTOs no son "alta y edición del mismo recurso" — son dos flujos de negocio
distintos (autorregistro público vs. alta administrativa) que **casualmente**
piden los mismos datos personales. Esa distinción de intención es real y una
clase base la desdibujaría un poco: alguien que lea `RegisterRequest extends
DatosPersonaBase` tiene que ir a mirar la base para saber qué pide el
formulario público, en vez de verlo de un vistazo.

**Costo actual de implementación:** bajo, mismo tamaño que BE-002.

**Cómo se integraría:** si se hace, mismo patrón que BE-002 — una clase base
`DatosPersonaRequest` con los cuatro campos + `telefono`. Dado el veredicto
"marginal" de arriba, se sugiere posponerlo hasta que aparezca un tercer DTO
que también los necesite (ya hay una candidata cercana si el padrón de
`CreateHabilitadoRequest` alguna vez suma `password` y `telefono` — hoy no los
tiene). Con dos usos y con la distinción semántica señalada, es razonable
dejarlo así por ahora.

## [ ] BE-004 — Construir un `Telefono` desde `TelefonoRequest` está copiado tres veces

**Ubicación:**
- `backend/src/main/java/com/retrorental/backend/service/AuthService.java:94-97`
- `backend/src/main/java/com/retrorental/backend/service/EmpleadoService.java:65-68` (`create`)
- `backend/src/main/java/com/retrorental/backend/service/EmpleadoService.java:91-94` (`update`)

**Descripción:** las tres veces es el mismo bloque de tres líneas:

```java
Telefono telefono = new Telefono();
telefono.setCodigoArea(request.getTelefono().getCodigoArea());
telefono.setTelefono(request.getTelefono().getNumero());
```

Es la duplicación más mecánica de todo el documento — no esconde ninguna
decisión de negocio, es un mapeo de campos 1 a 1 entre dos clases con nombres
de propiedad ligeramente distintos (`numero` en el DTO, `telefono` en el
embeddable).

**¿Vale la pena arreglarlo?** Sí — es el hallazgo más barato de arreglar de
todo el documento y el que menos discusión de diseño requiere. No hay
argumento en contra.

**Costo actual de implementación:** trivial, minutos.

**Cómo se integraría:** un método estático de fábrica en el propio
`Telefono` (`Telefono.desde(TelefonoRequest request)`) o, si se prefiere no
acoplar el embeddable de persistencia a un DTO de la capa web, un método
`private static Telefono toTelefono(TelefonoRequest)` en una clase de mapeo
compartida entre `AuthService` y `EmpleadoService` (o simplemente uno de los
dos llama al método del otro, ya que `AuthService` no depende hoy de
`EmpleadoService`). La primera opción es más simple y consistente con que
`Telefono` ya vive en `model.embeddable` sin depender de la capa `dto`.

## [ ] BE-005 — El mismo `normalizar(String)` (trim + `""` → `null`) está en dos servicios

**Ubicación:**
- `backend/src/main/java/com/retrorental/backend/service/VehiculoService.java:218-224`
- `backend/src/main/java/com/retrorental/backend/service/HerramientaService.java:81-87`

**Descripción:** los dos métodos privados son idénticos carácter por
carácter (mismo cuerpo, mismo Javadoc con la misma explicación sobre la
constraint `UNIQUE`). Vale aclarar que **no** es el mismo método que
`HabilitadoService.normalizar` (ese saca acentos y pasa a mayúsculas para
comparar apellidos — una regla de negocio distinta que coincide en el nombre
por casualidad); confundir los dos sería el error opuesto, así que conviene
que el nombre del método compartido lo deje claro si se extrae.

**¿Vale la pena arreglarlo?** Sí, mismo criterio que BE-004: es utilería sin
lógica de negocio, cero ambigüedad sobre qué hace.

**Costo actual de implementación:** trivial.

**Cómo se integraría:** método estático en una clase de utilidad
(`Strings.trimToNull(String)`, siguiendo el nombre que ya usa Apache Commons
Lang para esta exacta operación, por si el equipo prefiere sumar esa
dependencia en vez de escribir la propia) o un método `default` en una
interfaz pequeña que ambos servicios puedan implementar. Dado que es una
función pura de una línea de lógica, un método estático package-private es
más simple que crear una interfaz nueva para esto — no hace falta más
ceremonia que la extracción misma.
