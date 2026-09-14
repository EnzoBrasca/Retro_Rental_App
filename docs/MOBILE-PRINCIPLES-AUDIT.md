# Auditoría de principios (SOLID / DRY / YAGNI) — Front-end (mobile)

Fecha de la auditoría: 2026-08-20

**Alcance.** `mobile/` completo salvo `assets/` y configuración de build: rutas y navegación
(`app/`, Expo Router), componentes (`components/`), capa de servicios (`services/`),
contextos y hooks (`context/`, `hooks/`), tokens de diseño y utilidades (`constants/`).
44 archivos fuente TypeScript/TSX (sin contar los `.test.ts`), ~7.480 líneas, revisados
completos — no por muestreo.

**Qué NO cubre este documento.**

- **No cubre corrección funcional, manejo de estado, rendimiento, accesibilidad, código
  muerto ni tooling.** Todo eso ya está auditado y en gran parte resuelto en
  `docs/FRONTEND-AUDIT.md`. Donde un hallazgo de ese documento se cruza con una violación de
  principio de diseño (por ejemplo, la duplicación de mapeadores `Ticket → Row` de `DATA-00`),
  este documento lo referencia en vez de reabrirlo como si fuera nuevo.
- **No cubre seguridad.** Eso está en `docs/SECURITY-AUDIT.md`.
- **No cubre el backend.** Eso está en `docs/BACKEND-AUDIT.md`.
- Este documento audita exclusivamente **SOLID, DRY e YAGNI** (y separación de
  responsabilidades como consecuencia directa de SOLID) a nivel de arquitectura de
  componentes, hooks y módulos. No es una auditoría de estilo de código.

## Cómo usar este documento

Mismo criterio que las otras auditorías del repo: cada hallazgo tiene una casilla. Al
implementar el arreglo, marcá la casilla y agregá el commit o PR donde se resolvió. `[~]` es
un arreglo parcial, con el motivo escrito en el propio hallazgo.

Los IDs llevan el prefijo `MOB-`.

## Estado general

| Severidad | Total | Resueltas | Parciales | Pendientes |
| --- | --- | --- | --- | --- |
| Crítica | 1 | 0 | 0 | 1 |
| Alta | 1 | 0 | 0 | 1 |
| Media | 3 | 0 | 0 | 3 |
| Baja | 2 | 0 | 0 | 2 |
| **Total** | **7** | **0** | **0** | **7** |

## Contexto de escala — leer antes de priorizar

Ningún hallazgo de este documento está roto hoy. La app compila, pasa `tsc --strict`, tiene
0 problemas de ESLint y 35 tests en verde (ver `docs/FRONTEND-AUDIT.md:BLD-00`). La pregunta
que ordena la severidad acá **no es** "¿esto falla ahora?" sino **"¿esto se rompe cuando el
negocio crece?"**: cuando se agregue el quinto ABM del panel de administrador, cuando la
lógica de precios de combustible sume una regla más, cuando alguien nuevo tenga que tocar un
archivo de 1.750 líneas sin conocer todo lo que hay adentro. Una violación de SRP o DRY no
tira la app: hace que el próximo cambio cueste más de lo que debería y que un bug, una vez
encontrado, haya que arreglarlo en más de un lugar.

## Lo que ya está bien (no tocar)

- **`services/` es el módulo mejor resuelto del proyecto.** Cada archivo (`auth.ts`,
  `tickets.ts`, `vehiculos.ts`, `herramientas.ts`, `catalogos.ts`, `empleados.ts`,
  `habilitados.ts`, `personas.ts`, `stats.ts`, `imagenes.ts`) tiene una sola responsabilidad
  —espejar un recurso del backend— y ninguno sabe nada de React. Es SRP y DIP de manual: las
  pantallas dependen de funciones tipadas, no arman URLs ni parsean respuestas a mano.
- **`services/api.ts`** concentra el único cliente HTTP (headers, `BASE_URL`, manejo de 401,
  multipart) en un solo lugar. Ningún `fetch` suelto en el resto del proyecto.
- **`services/session.ts`** resuelve un problema real de acoplamiento sin recurrir a un
  import circular ni a un singleton de React: `api.ts` no puede llamar a `useAuth()`, así que
  el 401 viaja por un registro de callbacks que `AuthProvider` suscribe. Inversión de
  dependencia bien aplicada.
- **`services/sessionStorage.ts`** separa el secreto (token → SecureStore) del resto del
  perfil (→ AsyncStorage) con el porqué escrito, incluido el límite de ~2048 bytes de
  SecureStore en iOS que explica por qué NO se guarda el objeto entero. Responsabilidad
  única y bien delimitada.
- **`services/rutas.ts`** es la única fuente de la regla "a qué ruta corresponde una
  sesión". Se evalúa en dos lugares (`app/index.tsx` y `app/_layout.tsx`) a propósito, pero
  la regla en sí está escrita una sola vez — el propio comentario del archivo explica que
  tenerla duplicada fue el error a evitar.
- **`components/auth/AuthShell.tsx`** ya resolvió la duplicación entre Login y Registro: las
  15 claves de estilo idénticas y el bloque de marca/conmutador que antes vivían copiados en
  los dos archivos ahora están en un solo lugar (`authStyles`, exportado y reutilizado por
  ambas pantallas). Es el ejemplo a seguir para el resto del proyecto.
- **`services/imagenes.ts:comprimirTicket`** se llama una sola vez por foto y el mismo
  archivo comprimido se reutiliza tanto para `/tickets/analyze` (OCR) como para el alta en
  `/tickets` — no hay dos compresiones ni dos códigos de compresión.
- **`constants/theme.ts`** es la única fuente de colores, tipografía y radios. Ya no quedan
  literales hexadecimales sueltos en `app/` ni `components/` (ver `FRONTEND-AUDIT.md:UI-08`).
- **`constants/labels.ts:parseNumero`/`parseEntero`** y **`constants/validation.ts`**
  centralizaron reglas que antes estaban copiadas y ya habían divergido una vez (ver
  `FRONTEND-AUDIT.md:DATA-02` y `UI-03`). Es DRY aplicado después de pagar el costo de no
  tenerlo — vale la pena leerlos como referencia.
- **`hooks/useFetch.ts`** es un hook genérico de fetch-on-mount reutilizado por ocho
  consumidores distintos, con manejo de condición de carrera centralizado en un solo lugar
  en vez de reimplementado por pantalla.
- **`components/fuel/ScreenState.tsx`** (`Loading`, `ErrorState`, `EmptyState`) evita que
  cada pantalla reinvente su spinner y su mensaje de error.
- **`components/fuel/OptionChips.tsx`, `FilterDropdown.tsx` y `BarChart.tsx`** son
  primitivas de UI genéricas (tipadas sobre `T extends string | number`), reutilizadas tanto
  por pantallas de operario como del panel de administrador.
- **`components/admin/TicketsABM.tsx` ya fue extraído** del archivo del panel de
  administrador — su propio comentario de cabecera lo dice: *"Vive en su propio archivo y no
  dentro de la pantalla del panel porque esa ya pasaba las 900 líneas con dos ABMs
  adentro."* Es la prueba de que el equipo ya identificó y aplicó el patrón correcto una vez;
  `MOB-001` es, en el fondo, pedir que se termine de aplicar a lo que quedó atrás.

---

# CRÍTICAS

## [ ] MOB-001 — `app/(administrador)/index.tsx` es un God File de 1.750 líneas con cinco responsabilidades no relacionadas

**Dónde:** `app/(administrador)/index.tsx` completo (1.750 líneas). Contiene:

| Función | Líneas | Responsabilidad |
| --- | --- | --- |
| `AdministradorScreen` | 119–179 | Shell de tabs del panel |
| `Analytics` | 223–456 | Dashboard con filtros, KPIs y gráficos |
| `VehiclesABM` | 527–1011 | ABM de vehículos y herramientas (form + lista + baja) |
| `PersonalABM` | 1050–1077 | Subtabs Empleados/Habilitados |
| `EmpleadosList` | 1079–1333 | ABM completo de empleados |
| `HabilitadosList` | 1347–1520 | ABM completo del padrón de habilitados |
| `Kpi` | 1522–1545 | Tarjeta de KPI reutilizada por `Analytics` |
| `styles` | 1547–1750 | ~200 líneas de estilos de TODO lo anterior en un solo `StyleSheet` |

**Por qué viola el principio.** Es la violación de **Single Responsibility** más clara del
proyecto: un archivo que cambia por cinco motivos distintos y no relacionados entre sí
—ajustar un filtro de analítica, agregar un campo al ABM de vehículos, cambiar la validación
de empleados, tocar el padrón de habilitados— es un archivo que un solo desarrollador no
puede tener completo en la cabeza, y donde cualquiera de esos cinco cambios corre el riesgo
de pisar el estado o los estilos de otro. `TicketsABM` ya demostró que la extracción es
directa: la propia app tiene el patrón funcionando desde hace rato, solo que aplicado a un
componente de los cinco.

**¿Vale la pena arreglarlo?** Sí, sin ambigüedad. No es una abstracción especulativa para un
problema futuro — el archivo YA tiene 1.750 líneas y ya mezcla estado, validación y estilos
de cinco pantallas distintas. Es la violación con más costo compuesto de las siete: cada ABM
nuevo que se agregue (y el patrón del negocio —flota, herramientas, empleados,
habilitados— sugiere que van a seguir apareciendo entidades para gestionar) va a este mismo
archivo si nadie corta el patrón ahora.

**Costo actual de implementación.** Medio, y sobre todo mecánico — no hay lógica nueva que
escribir, es mover código siguiendo un patrón que ya existe en el repo:

1. `components/admin/Analytics.tsx` — mueve `Analytics`, `Kpi`, `toISODate`, `shiftAnchor`,
   `isFutureDay`, `TODOS_VEHICULO`, `CHART_VIEW_OPTS`, `RANGES`, `CHART_COLORS` y sus estilos.
2. `components/admin/VehiclesABM.tsx` — mueve `VehiclesABM` y sus tipos (`FormState`,
   `FlotaItem`, `emptyForm`, `formFrom`, `formFromHerramienta`) y estilos.
3. `components/admin/PersonalABM.tsx` (o dos archivos, `EmpleadosList.tsx` y
   `HabilitadosList.tsx`, si se prefiere granularidad fina como ya tiene `TicketsABM`).
4. `app/(administrador)/index.tsx` queda con `AdministradorScreen` y el `styles` propio del
   shell de tabs (`safe`, `header`, `topTabs`, etc.) — probablemente por debajo de las 200
   líneas, comparable al resto de los layouts del proyecto.

Los estilos que hoy comparten `panel`, `panelLabel`, `filterRow`, `fieldHint` entre
`Analytics` y `VehiclesABM`/`EmpleadosList` (y que además están duplicados byte a byte en
`TicketsABM.tsx`, ver el propio comentario de ese archivo: *"Los tres de abajo son los mismos
valores que usa la analítica"*) son un buen candidato para un `components/admin/styles.ts`
compartido en el mismo movimiento — evita fijar la duplicación que ya existe entre el panel y
`TicketsABM` en tres lugares en vez de dos.

**Riesgo del cambio:** bajo. Es reubicación de código, no reescritura de lógica; cada
componente extraído mantiene sus mismas props (ninguno recibe props hoy — todos leen del
mismo `useFetch`/`useState` local) y su mismo comportamiento. No hay tests de componentes que
cubran estas pantallas (ver `FRONTEND-AUDIT.md:BLD-00`), así que conviene verificar a mano en
el APK después del movimiento: las cuatro pestañas del panel y sus ABMs.

---

# ALTAS

## [ ] MOB-002 — `app/(empleado)/escanear.tsx`: un componente de 887 líneas mezcla cámara, OCR, lógica de precios y envío

**Dónde:** `app/(empleado)/escanear.tsx`, componente `EscanearScreen` (líneas 45–644, más
243 líneas de estilos). Responsabilidades entremezcladas en el mismo cuerpo de función:

- Estado y permisos de cámara (líneas 54–61, 224–241).
- Flujo de OCR: captura, compresión, análisis, autocompletado condicional (líneas 161–222).
- **Resolución de precio**, que es lógica de negocio pura sin nada de UI: qué precio aplica
  según proveedor + combustible, el caso especial de MEZCLA sin precio propio, si el precio
  fue corregido a mano (líneas 250–311).
- Validación y armado del payload de envío (líneas 313–369).
- Tres pantallas distintas (`capture`, `analyzing`, `form`) como ramas condicionales del
  mismo `return` (líneas 371–644).

**Por qué viola el principio.** SRP otra vez, pero con un matiz distinto a `MOB-001`: acá el
problema no es que sea largo por tener varias pantallas pegadas (son etapas de UN mismo
flujo, eso está bien modelado con `Stage`), sino que la **lógica de precios —dinero, la parte
que más importa auditar— vive mezclada con JSX y no se puede probar sin renderizar todo el
componente.** Nada de `precioSel`, `precioBase`, `esMezclaSinPrecioPropio` ni
`precioFueCorregido` tiene un test, a diferencia de `parseNumero` o `formatDay`, que sí lo
tienen precisamente por vivir en un módulo separado de la UI.

**¿Vale la pena arreglarlo?** Parcialmente, y hay que ser específico sobre QUÉ extraer.
Descomponer las tres etapas (`capture`/`analyzing`/`form`) en subcomponentes separados
**no** vale la pena hoy: son etapas de un solo flujo con bastante estado compartido
(`fotoUri`, los campos del formulario), partirlas fuerza a pasar diez props de un lado a
otro y no resuelve nada real — sería refactor por estética. Extraer la **lógica de precios**
sí vale la pena: es la única parte de este archivo que es negocio puro, sin JSX, con
entradas y salidas claras, y es exactamente el tipo de código que ya se prueba en este
proyecto (`constants/labels.test.ts`, `services/vehiculos.test.ts`).

**Costo actual de implementación.** Bajo para la parte que importa. Las líneas 250–311 ya
están escritas como cálculos derivados sin efectos secundarios (salvo el `useEffect` de
263–304, que sincroniza el campo editable con la base calculada). Extraerlas a una función
pura o a un hook (`hooks/usePrecioCarga.ts`) que reciba `(precios, idProveedor,
tipoCombustibleSel, idHerramienta)` y devuelva `{ precioBase, esMezclaSinPrecioPropio }` no
cambia el comportamiento, solo lo saca del componente y lo hace testeable con Jest sin montar
React Native.

**Cómo se integraría.** `hooks/usePrecioCarga.ts` (o `services/tickets.ts`, al lado de
`CreateTicketPayload`, si se prefiere que sea función pura sin `useMemo` propio) exporta la
resolución de precio; `EscanearScreen` lo consume y le quedan las ~60 líneas que hoy ocupa esa
sección reducidas a una llamada. El `useEffect` que sincroniza `precioEditado` con
`ultimoPrecioAplicado` se queda en el componente (es estado de UI, no negocio), pero ahora
depende de un valor ya resuelto y testeado aparte.

---

# MEDIAS

## [ ] MOB-003 — El patrón de formulario CRUD con confirmación de baja se reimplementa tres veces dentro del mismo archivo

**Dónde:** las tres están en `app/(administrador)/index.tsx`:

- `VehiclesABM` → `removeVehiculo` (líneas 653–669) y `removeHerramienta` (671–687).
- `EmpleadosList` → `remove` (líneas 1144–1160).
- `HabilitadosList` → `quitar` (líneas 1389–1409).

Las cuatro funciones tienen la misma forma exacta: `Alert.alert(título, mensaje, [Cancelar,
{ text: acción, style: 'destructive', onPress: async () => { try { await
desactivarX(id); await refetch(); } catch (e) { Alert.alert('Error', ...) } } }])`. A eso se
suma que `VehiclesABM`, `EmpleadosList` y (con `creando` en vez de `editing`) `HabilitadosList`
repiten el mismo esqueleto de estado — `editing`/`saving`/`formError`, `openNew`, `save()`
con `try/catch/finally` — con el mismo orden de pasos.

**Por qué viola el principio.** DRY sobre la **forma** del código, no solo sobre texto
literal: es el mismo comportamiento (confirmar destructivamente, desactivar, refrescar,
avisar error) reimplementado a mano cuatro veces. Si mañana se decide, por ejemplo, deshacer
la baja en vez de solo confirmarla, o cambiar el texto del botón "Cancelar" por
"Cancelar" → "Volver", hay que acordarse de tocar los cuatro lugares — el mismo tipo de
deriva que ya le pasó una vez al proyecto (ver el comentario de `AuthShell` sobre el color de
fondo que login tenía y register no).

**¿Vale la pena arreglarlo?** Con matices. Un hook CRUD genérico (`useEntityForm<T>()`) que
intente cubrir los cuatro casos **sería sobre-ingeniería hoy**: cada formulario tiene campos y
validaciones bien distintas (vehículo vs. herramienta vs. empleado vs. habilitado), y forzarlos
a una abstracción común los volvería más difíciles de leer, no menos — exactamente el YAGNI
al revés que pide evitar la consigna de esta auditoría. Lo que sí vale la pena, porque es
chico y no fuerza nada, es extraer el wrapper de confirmación destructiva: las cuatro
llamadas a `Alert.alert` con `style: 'destructive'` son, letra por letra, la misma estructura
con tres strings distintos.

**Costo actual de implementación.** Bajo. Es una función de 15-20 líneas.

**Cómo se integraría.** Un helper `confirmarBaja({ titulo, mensaje, onConfirm, onError })`
en `components/admin/` (o en `components/ui/`, si se lo ve como utilidad general de
confirmación destructiva) que encapsula el `Alert.alert` de dos botones con el `try/catch`
de la acción. Los cuatro lugares pasan de ~15 líneas a 3. No resuelve la duplicación del
`editing`/`save()` (esa es la que no conviene forzar todavía), pero sí la pieza que se repite
sin variación real.

---

## [ ] MOB-004 — El markup de fila de un ABM (ícono + nombre + subtítulo + editar/borrar) se repite cuatro veces

**Dónde**, todo dentro de `app/(administrador)/index.tsx`:

- Card de vehículo, líneas 887–952.
- Card de herramienta, líneas 960–1006.
- Card de empleado, líneas 1274–1328.
- Card de habilitado, líneas 1487–1516.

Cada una arma la misma estructura visual —`abmCard` → `abmHeader` → ícono en `abmIcon` +
columna con `abmName`/`abmSub`/`abmEstado` + par de `Pressable` de 34×34 con
`accessibilityLabel` dinámico para editar y dar de baja— con el contenido interno adaptado a
cada entidad, pero la estructura, los estilos y el patrón de accesibilidad son el mismo
código repetido a mano.

**Por qué viola el principio.** DRY de estructura visual + comportamiento (mismo patrón de
accesibilidad, mismo layout), consecuencia directa de que las cuatro listas conviven en el
mismo God File (`MOB-001`) sin un componente compartido que las represente.

**¿Vale la pena arreglarlo?** Sí, y es de bajo costo hacerlo como parte del mismo movimiento
que `MOB-001`: al extraer cada ABM a su archivo, es el momento natural de notar que las
cuatro filas son la misma forma. Hacerlo antes de separar los archivos sería más trabajo
(cuatro `import`s cruzados en un archivo que ya tiene 1.750 líneas); hacerlo después es casi
gratis.

**Costo actual de implementación.** Bajo-medio, y conviene secuenciarlo después de `MOB-001`.

**Cómo se integraría.** Un componente `AbmRow` en `components/admin/` con props `{ icon:
ReactNode; title: string; subtitle: string; estado?: { label: string; color: string };
onEdit: () => void; onDelete: () => void; disabled?: boolean }`. El ícono se pasa como
`ReactNode` (no como componente `FC<SvgProps>`) porque una de las cuatro filas usa un SVG y
otra un emoji en `<Text>` — forzarlos al mismo tipo sería más artificial que útil.

---

## [ ] MOB-005 — Dos mapeadores `Ticket → Row` casi idénticos, ya señalado en `FRONTEND-AUDIT.md` y todavía sin unificar

**Dónde:**

- `app/(empleado)/historial.tsx`, `useMemo` de `rows` (líneas 130–168).
- `components/admin/TicketsABM.tsx`, función `toRow` (líneas 331–364).

Las dos funciones resuelven un `Ticket` contra los mismos catálogos (vehículos, herramientas,
proveedores) con la misma lógica: cuál de `idVehiculo`/`idHerramienta` viene con valor, el
mismo fallback `Vehículo #${id}` / `Herramienta #${id}` cuando el catálogo no tiene la fila, y
el mismo criterio de que el costo sale de `t.litros * t.precioUnitario` (el ticket), no del
catálogo de precios vigentes.

**Por qué viola el principio.** DRY sobre lógica de negocio real, no solo sobre forma —es el
mismo cálculo escrito dos veces, y **ya divergió una vez**: `docs/FRONTEND-AUDIT.md:DATA-00`
documenta que el mapeador de `historial.tsx` calculaba el costo mal (resolviendo contra
`GET /precios`, que solo trae precios vigentes) mientras el de `TicketsABM.toRow` ya usaba
`t.precioUnitario` correctamente. El bug de cálculo está resuelto en las dos puntas hoy, pero
la causa de fondo —dos implementaciones de la misma función— sigue ahí, y el propio `DATA-00`
lo deja anotado como "arreglo estructural" pendiente: *"Después de corregir el cálculo,
unificarlos en una sola función (...). Mientras sigan siendo dos, van a volver a
divergir."* Este hallazgo no es nuevo: es la versión SOLID/DRY del mismo problema que
`FRONTEND-AUDIT.md` ya dejó marcado, y sigue pendiente al día de esta auditoría.

**¿Vale la pena arreglarlo?** Sí — es DRY sobre un cálculo de dinero, con precedente real de
haber divergido una vez y haber tardado una auditoría en notarse en un solo lado.

**Costo actual de implementación.** Bajo. `TicketsABM.toRow` ya tiene la forma correcta y ya
resuelve el caso general (vehículo o herramienta); `historial.tsx` no necesita `operario`
(el historial es siempre del empleado autenticado). Es una función parametrizable por si
incluye `operario` o no.

**Cómo se integraría.** Tal como ya proponía `DATA-00`: una función `resolverTicketRow(t:
Ticket, catalogos: { vehiculos, herramientas, proveedores, empleados? }): Row` al lado del
tipo `Row` en `components/fuel/LoadDetailModal.tsx` (que es donde ya vive el tipo, por ser el
componente compartido que lo consume), usada desde `historial.tsx` y `TicketsABM.tsx`.

---

# BAJAS

## [ ] MOB-006 — `estadoStyle` (colores de `Estado`) duplicado byte a byte en dos pantallas

**Dónde:**

- `app/(empleado)/index.tsx`, líneas 40–44.
- `app/(administrador)/index.tsx`, líneas 83–87.

```ts
const estadoStyle: Record<Estado, { bg: string; color: string }> = {
  DISPONIBLE: { bg: colors.greenBg, color: colors.greenText },
  EN_USO: { bg: colors.amberBg, color: colors.amberText },
  EN_MANTENIMIENTO: { bg: colors.dangerBg, color: colors.danger },
};
```

Idéntico en los dos archivos, símbolo por símbolo.

**Por qué viola el principio.** DRY simple: es un mapeo de datos, no de comportamiento, y ya
existe un lugar natural para él —`constants/labels.ts`, al lado de `estadoLabel`, que es
exactamente el mismo tipo de mapeo (`Record<Estado, string>`) y ya vive ahí.

**¿Vale la pena arreglarlo?** Sí, es de las más baratas de esta lista. El riesgo de dejarlo
es el mismo patrón de deriva que ya le pasó al proyecto con los estilos de login/registro
antes de `AuthShell`: si mañana se agrega un cuarto `Estado` o cambia un color, hay que
acordarse de los dos archivos.

**Costo actual de implementación.** Muy bajo — mover el objeto y agregar un `import`.

**Cómo se integraría.** Mover `estadoStyle` a `constants/labels.ts` junto a `estadoLabel`
(mismo patrón: `Record<Estado, {...}>`), importarlo en `app/(empleado)/index.tsx` y
`app/(administrador)/index.tsx` en vez de declararlo dos veces.

---

## [ ] MOB-007 — `FilterRow` (flota del operario) y `OptionChips` son dos implementaciones del mismo patrón de selección por chips

**Dónde:**

- `app/(empleado)/index.tsx`, componente `FilterRow` (líneas 73–104): chips en una fila con
  scroll horizontal, single-select con `null` como "Todos".
- `components/fuel/OptionChips.tsx` (archivo completo): chips que se envuelven
  (`flexWrap`), single-select, con soporte de `disabledKeys`.

Las dos son genéricas (`<T extends string>` / `<T extends string | number>`), comparten casi
el mismo estilo visual (`chip`, `chipActive`, `chipText`, `chipTextActive` con valores muy
similares) y el mismo comportamiento de selección — la diferencia real es el contenedor:
`ScrollView horizontal` en una, `View` con `flexWrap` en la otra.

**Por qué viola el principio.** Es DRY, pero de la variedad más discutible: dos
implementaciones de "elegí una opción entre varias, mostrada como chips" que difieren en una
sola decisión de layout (scroll vs. wrap), justificada porque `FilterRow` necesita cinco
filtros en una franja angosta (la barra de filtros de Flota) y `OptionChips` necesita
mostrar todas las opciones a la vez en un formulario (el operario tiene que ver todos los
combustibles disponibles, no scrollearlos).

**¿Vale la pena arreglarlo?** No, no todavía — y vale decirlo explícitamente para no caer en
el error contrario a `MOB-001`: unificar dos componentes de 30 líneas cada uno en una única
variante configurable (`layout: 'wrap' | 'scroll'`) agrega una prop, una rama condicional y
un nivel de indirección para ahorrar, en la práctica, unas 15 líneas de JSX. Es la clase de
abstracción prematura que la consigna de esta auditoría pide señalar como "YAGNI al revés".
Queda documentado para que, si aparece un **tercer** lugar que necesite este mismo patrón de
selección, sea la señal de unificar los tres — no antes.

**Costo actual de implementación.** N/A (no se recomienda actuar todavía).

**Cómo se integraría, si en el futuro se justifica.** `OptionChips` ganaría una prop
`scroll?: boolean` que cambia el contenedor de `View` a `ScrollView horizontal`, y `FilterRow`
desaparecería a favor de `OptionChips scroll value={tipoF} onChange={setTipoF}`.
