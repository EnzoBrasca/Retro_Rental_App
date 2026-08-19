# Auditoría de calidad de código — Front-end (mobile)

Fecha de la auditoría: 2026-08-18
Alcance: `mobile/` completo — 43 archivos fuente, 6.336 líneas de TypeScript/TSX. Rutas y
navegación (`app/`, Expo Router), componentes (`components/`), capa de servicios
(`services/`), contextos y hooks (`context/`, `hooks/`), tokens de diseño (`constants/`),
configuración de build (`app.json`, `eas.json`, `metro.config.js`, `tsconfig.json`).

**Esta auditoría NO cubre seguridad.** Eso está en `docs/SECURITY-AUDIT.md`. Tampoco cubre
el backend: para eso está `docs/BACKEND-AUDIT.md`. Acá se audita calidad del front:
corrección de lo que se muestra y se envía, manejo de estado, código muerto, rendimiento,
accesibilidad y deuda de tooling.

Tres hallazgos críticos cruzan la frontera con el backend (`DATA-00`, `DATA-01`). Están
acá porque el síntoma lo produce el cliente, pero el arreglo completo toca las dos puntas y
está indicado en cada uno.

## Cómo usar este documento

Mismo criterio que las otras dos auditorías: cada hallazgo tiene una casilla. Al
implementar el arreglo, marcá la casilla y agregá el commit o PR donde se resolvió. Lo que
sigue sin marcar es lo que falta. `[~]` es un arreglo parcial, con el motivo escrito en el
propio hallazgo.

Los IDs llevan prefijo por área:

| Prefijo | Área |
| --- | --- |
| `DATA` | Corrección de los datos que se muestran o se envían |
| `STATE` | Manejo de estado, ciclo de vida y efectos |
| `NAV` | Navegación y rutas |
| `UI` | Componentes, formularios y experiencia de uso |
| `PERF` | Rendimiento y consumo de red |
| `A11Y` | Accesibilidad |
| `DEAD` | Código muerto y duplicación |
| `BLD` | Build, tooling y documentación del repo |

## Contexto de escala — leer antes de priorizar

A diferencia de la auditoría del backend, acá **no todo es una mina plantada**. La
distinción importa para priorizar:

- `DATA-00` **está roto hoy, en producción, para todos los operarios.** No espera a que
  crezca nada: se activó solo, la primera vez que cambió un precio de combustible.
- `STATE-00` y `DATA-01` corrompen datos en silencio, hoy, cada vez que se dan las
  condiciones. No fallan con un error: guardan el número equivocado y siguen.
- El resto (rendimiento, accesibilidad, código muerto, tooling) sí sigue la lógica del
  backend: cuesta poco hoy y mucho cuando la flota pase de decenas a cientos de vehículos,
  o cuando entre alguien nuevo a mantener esto.

El contexto de uso también pesa. Son teléfonos de empleados en obra, con conexión mala e
intermitente. Cada request de más y cada foto sin comprimir se pagan en el yacimiento, no
en la máquina de quien programó.

## Estado general

| Severidad | Total | Resueltas | Parciales | Pendientes |
| --- | --- | --- | --- | --- |
| Crítica | 3 | 3 | 0 | 0 |
| Alta | 6 | 2 | 1 | 3 |
| Media | 8 | 0 | 0 | 8 |
| Baja | 14 | 1 | 0 | 13 |
| **Total** | **31** | **6** | **1** | **24** |

**Fases 1 y 2 cerradas** (rama `fix/fase-1-frontend-criticos`, que las acumula: no se
despliega hasta terminar la auditoría, para no generar un APK por fase). El backend pasó de
304 a 305 tests, y `tsc --noEmit` sigue limpio.

`STATE-01` queda en `[~]`: la condición de carrera está resuelta, pero la cancelación real
de la conexión (`AbortController`) se difiere.

Una corrección a la propia auditoría: al implementar `STATE-00` se verificó que **la mitad
del hallazgo no era válida** (el arrastre de `precioEditado`). El detalle está escrito en el
hallazgo. Queda como recordatorio de que un hallazgo sin test que lo reproduzca es una
hipótesis, no un hecho — que es exactamente lo que argumenta `BLD-00`.

Orden sugerido de ataque:

1. **Fase 1 — lo que está roto hoy:** `DATA-00`, `STATE-00`, `DATA-01`. Son tres arreglos
   chicos y acotados, y los tres producen datos incorrectos en producción ahora mismo.
2. **Fase 2 — lo que se pierde en silencio:** `UI-00` (cuentas sin username), `STATE-01` y
   `PERF-00` (condiciones de carrera).
3. **Fase 3 — la red de contención:** `BLD-00`. Sin ESLint ni un solo test, cada arreglo de
   las fases anteriores es un acto de fe. Va después de lo urgente pero antes que todo lo
   demás, porque es lo que evita que esto se vuelva a llenar.
4. **Fase 4 — el resto**, por severidad.

## Lo que ya está bien (no tocar)

Vale decirlo antes de la lista de problemas, porque hay decisiones acá que están mejor
resueltas que en muchos proyectos con más gente encima:

- **`tsc --noEmit` pasa limpio con `strict: true`.** Cero errores de tipo en 6.336 líneas.
  No hay un solo `@ts-ignore` en el proyecto, y el único `as unknown as Blob` está
  justificado con un comentario (React Native adjunta archivos como `{uri, name, type}`).
- **La capa de servicios está bien separada.** `services/` no sabe nada de React y las
  pantallas no arman URLs a mano. Cada módulo documenta de qué DTO del backend es espejo.
- **`services/sessionStorage.ts` es ejemplar.** El token en SecureStore y el perfil en
  AsyncStorage, con el porqué escrito: el límite de ~2048 bytes de SecureStore en iOS, el
  motivo de no migrar la sesión vieja en vez de moverla, y el guard de `Platform.OS`
  para que la app no reviente en web. Eso es documentación que evita que alguien
  "simplifique" el módulo dentro de seis meses.
- **`services/session.ts` resuelve bien un problema real:** `api.ts` no puede llamar a
  `useAuth()`, así que el 401 viaja por un registro de callbacks en vez de por un import
  circular o un singleton de React.
- **La caducidad de sesión se ataca por tres vías** (rehidratación, vuelta a primer plano,
  401 del backend) y las tres están explicadas.
- **`app/(empleado)/historial.tsx` está construido como corresponde:** `FlatList` real,
  `renderItem` con `useCallback`, `LoadRow` memoizado con props estables, paginación por
  `onEndReached` y deduplicación por id al concatenar páginas. Es la pantalla de
  referencia: cuando haya que arreglar `PERF-01`, el modelo a copiar es ésta.
- **`constants/labels.ts:formatDay` maneja el off-by-one de las fechas date-only.** Parsea
  los componentes a mano en vez de dejar que `new Date("2026-07-05")` los interprete como
  medianoche UTC y corra el día en UTC-3. Es exactamente el bug que casi nadie ve venir.
- **Los comentarios explican POR QUÉ, no QUÉ.** `consumoParaStats` documenta por qué la
  inversión a km/L se hace en el cliente y no en el servidor (un APK ya instalado
  renderizaría el número nuevo con la etiqueta vieja). Ese nivel de razonamiento escrito
  es lo más valioso que tiene este código.

---

# CRÍTICAS

## [x] DATA-00 — El historial del operario muestra `$0` en toda carga cuyo precio ya no está vigente

> **Resuelto** en `fix/fase-1-frontend-criticos`. El mapeador del historial usa
> `t.precioUnitario` y `getPrecios()` salió del `useFetch` de la pantalla (un request menos
> por apertura). La unificación de los dos mapeadores `Ticket → Row` **no** se hizo: se
> difiere a la Fase 3, para hacerla con tests que la respalden en vez de a ciegas.

**Dónde:** `app/(empleado)/historial.tsx:110-146` (el `useMemo` que arma las filas), en
particular la línea 124.

**Qué pasa.** Para calcular el costo de cada ticket, el historial resuelve `t.idPrecio`
contra el catálogo que trajo de `GET /precios`:

```ts
const precioById = new Map<number, Precio>(data.precios.map((p) => [p.id, p]));
// ...
const precio = precioById.get(t.idPrecio);
const unitario = precio ? precio.precioUnitario : 0;   // ← acá
// ...
precioUnitario: unitario,
costo: t.litros * unitario,
```

Pero `GET /precios` **no devuelve todos los precios: devuelve solo los vigentes.**
Verificado en el backend:

```java
// PrecioController.listar()  ->  precioService.listVigentes()
public List<PrecioResponse> listVigentes() {
    return precioRepository.findByFechaHastaIsNull().stream()   // ← solo fechaHasta == null
        .map(this::toResponse)
        .toList();
}
```

Un ticket de hace dos meses apunta a una fila de precio que desde entonces se cerró
(`fechaHasta` seteada). Esa fila no está en la respuesta, el `Map` no la tiene,
`precioById.get()` devuelve `undefined`, y el fallback pone **cero**.

**Consecuencia.** En el historial del operario, cada carga anterior al último cambio de
precio se muestra con importe **$0** en la card, y al abrir el detalle muestra
`Precio / L: $0` y `TOTAL: $0`. En Argentina el combustible se reajusta seguido: en la
práctica esto significa que **casi todo el historial del operario muestra cero**.

Lo delata que el panel del admin, sobre los mismos tickets, muestra el importe correcto:

```ts
// components/admin/TicketsABM.tsx:312-313  —  la forma correcta
precioUnitario: t.precioUnitario,
costo: t.litros * t.precioUnitario,
```

El `Ticket` ya trae `precioUnitario`, y su propio comentario en `services/tickets.ts` lo
dice: *"Precio por litro efectivamente aplicado a esta carga. El monto total no viaja: es
litros × este precio."* El historial ignora ese campo y sale a buscar un dato que ya tiene
en la mano.

**Por qué no lo agarró nadie.** Con la base recién cargada todos los precios están
vigentes, así que el lookup encuentra todo y el número sale bien. El bug se activa solo,
sin que nadie toque una línea, la primera vez que cambia un precio.

**El segundo error, que el primero tapa.** Aunque el lookup encontrara la fila, seguiría
estando mal: `CreateTicketPayload.precioUnitario` existe justamente para que el empleado
corrija el precio cuando el surtidor cobró otra cosa. Ese valor corregido vive en el
ticket, no en el catálogo. Resolviendo contra el catálogo, **una corrección de precio nunca
se vería en el historial**, ni siquiera con los precios vigentes.

**Arreglo.** Usar `t.precioUnitario`, igual que `TicketsABM.toRow`. Con eso, además,
`getPrecios()` deja de ser necesario en la carga del historial: es un request menos en cada
apertura de la pestaña, sobre la conexión del yacimiento.

**Arreglo estructural (el que evita el próximo).** Hay **dos mapeadores `Ticket → Row`**,
uno en cada pantalla, produciendo dos verdades distintas para el mismo ticket. Después de
corregir el cálculo, unificarlos en una sola función —al lado del tipo `Row`, en
`components/fuel/LoadDetailModal.tsx`— parametrizada por si resuelve `operario` o no.
Mientras sigan siendo dos, van a volver a divergir.

---

## [x] STATE-00 — `escanear` arrastra el odómetro del vehículo anterior

> **Resuelto a medias porque la otra mitad no existía.** Se agregó `setUsoAcumulado('')` al
> reset: ese arrastre es real y estaba confirmado.
>
> **La parte del precio de este hallazgo era incorrecta.** Al implementarlo se verificó
> contra el código: el reset hace `setIdProveedor(null)` (línea 108), y `precioSel` exige
> `idProveedor != null` (líneas 209-214), así que `precioBase` queda `undefined` en cada
> foco y la rama `if (!precioBase)` del efecto (245-248) **ya vacía `precioEditado` y el
> ref**. El análisis de abajo citó ese efecto con un `// ...` que se comió justamente esa
> rama, y concluyó de más. No se tocó nada ahí: habría sido código muerto.
>
> Vale como advertencia sobre esta auditoría entera: está leída, no ejecutada. Ver `BLD-00`.

**Dónde:** `app/(empleado)/escanear.tsx:99-112` (el `useFocusEffect` de reset).

**Qué pasa.** Al ganar foco, la pantalla resetea el formulario para no arrastrar una carga
previa. Resetea diez cosas:

```ts
useFocusEffect(
  useCallback(() => {
    setStage('form');
    setFotoUri(null);
    setError(null);
    setFechaCarga(null);
    setAnalysisNote(null);
    setIdVehiculo(paramVehiculoId);
    setIdHerramienta(paramHerramientaId);
    setIdProveedor(null);
    setLitros('');
    setTipoCombustibleHerramienta(null);
  }, [paramVehiculoId, paramHerramientaId]),
);
```

**Faltan dos: `usoAcumulado` y `precioEditado`.** Y `escanear` es un `Tabs.Screen`
(`app/(empleado)/_layout.tsx:70`, con `href: null`): una vez visitada **queda montada**, así
que su estado sobrevive entre entradas.

**El escenario, que es el flujo normal de un día de trabajo:**

1. El operario carga la Hilux. Anota `152340` km. Confirma. La app lo manda al historial.
2. Vuelve a Flota y toca la retroexcavadora CAT 320.
3. El reset corre: limpia litros, proveedor, foto… y deja **`152340`** en el campo de
   lectura.
4. La etiqueta ahora dice "Horas del horómetro" y el campo dice `152340`.
5. Si no lo borra, se manda `usoAcumulado: 152340` para una máquina que tiene 4.200 horas.

**Consecuencia.** El backend calcula el consumo real a partir del delta entre lecturas
consecutivas. Una lectura de otro vehículo no produce un error: produce un **consumo
calculado absurdo que se persiste y reemplaza al estimado**, y contamina la analítica del
vehículo desde ese punto en adelante. Es corrupción silenciosa del dato central del
negocio.

**El mismo agujero en el precio, y es peor porque es invisible.** `precioEditado` tampoco
se resetea, y el efecto que lo repuebla (líneas 238-249) se guarda contra un ref:

```ts
const ultimoPrecioAplicado = useRef<number | null>(null);
useEffect(() => {
  if (precioBase && ultimoPrecioAplicado.current !== precioBase.precioUnitario) {
    ultimoPrecioAplicado.current = precioBase.precioUnitario;
    setPrecioEditado(String(precioBase.precioUnitario));
  }
  // ...
}, [precioBase]);
```

Si el operario corrige el precio a `1.150` para el vehículo A y después entra al vehículo B
**con el mismo proveedor y el mismo combustible**, `precioBase` es literalmente el mismo
objeto, el ref ya tiene ese valor, la condición no se cumple y el efecto **no repuebla el
campo**. Queda `1.150`, `precioFueCorregido` da `true`, y se envía como corrección
deliberada del precio para una carga en la que nadie corrigió nada. A diferencia del
odómetro —donde un número de seis cifras en un campo de horas al menos llama la atención—
acá el valor es plausible y no lo mira nadie.

**Arreglo.** Agregar `setUsoAcumulado('')` y `setPrecioEditado('')` al reset, y limpiar
también `ultimoPrecioAplicado.current = null` (sin eso, el efecto sigue creyendo que ya
aplicó el precio y deja el campo vacío).

**Arreglo estructural.** Una lista de reset escrita a mano se desincroniza cada vez que
alguien agrega un `useState`: ya pasó dos veces en esta pantalla. Las alternativas, en
orden de solidez:

- Agrupar el formulario en **un solo objeto de estado** con un `resetForm()`, para que
  agregar un campo no requiera acordarse de nada.
- O montar la pantalla como **ruta con `key`** por ítem, para que React tire el estado en
  vez de que lo limpie una lista manual.

La segunda es la que hace imposible el bug, no solo improbable.

---

## [x] DATA-01 — Editar cualquier vehículo le pisa la fecha de último mantenimiento con la de hoy

> **Resuelto en las dos puntas.** Backend: `VehiculoResponse` expone
> `fechaUltimoMantenimiento` y `VehiculoService.toResponse` la mapea, con el test
> `VehiculoServiceTest#listAll_exponeLaFechaDeUltimoMantenimiento` escrito ANTES del cambio.
> Front: `formFrom` lee `v.fechaUltimoMantenimiento`; `todayISO()` queda solo en
> `emptyForm()`, que es el alta.
>
> **ORDEN DE DESPLIEGUE OBLIGATORIO: primero el backend.** Si sale el APK contra un backend
> que todavía no devuelve el campo, viaja `undefined`, el `@NotNull` de
> `UpdateVehiculoRequest` lo rechaza y **editar vehículos deja de funcionar por completo**.

**Dónde:** `app/(administrador)/index.tsx:420` (dentro de `formFrom`).

**Qué pasa.** `formFrom(v)` arma el formulario de edición a partir del vehículo. Todos los
campos salen del vehículo… menos uno:

```ts
const formFrom = (v: Vehiculo): FormState => ({
  identificador: v.identificador,
  modelo: v.modelo ?? '',
  tipoVehiculo: v.tipoVehiculo,
  // ...
  fechaUltimoMantenimiento: todayISO(),   // ← inventado, no viene del vehículo
  usoAcumulado: String(v.usoAcumulado),
  // ...
});
```

Y ese valor viaja en el `UpdateVehiculoPayload` de cada guardado.

**Consecuencia.** El admin entra a cambiar el estado de un camión a `EN_MANTENIMIENTO`,
guarda, y **la fecha de último mantenimiento del camión pasa a ser hoy** — aunque el
mantenimiento real haya sido hace ocho meses. No hay aviso, no hay confirmación: el campo
está en el formulario mostrando la fecha de hoy como si fuera el dato guardado. Cualquier
edición de cualquier campo destruye el historial de mantenimiento de ese vehículo.

**Por qué está así.** No es descuido del formulario: **`VehiculoResponse` no expone
`fechaUltimoMantenimiento`.** El cliente literalmente no tiene de dónde leer el valor real
(revisar la interfaz `Vehiculo` en `services/vehiculos.ts:168-198` — el campo no está). Al
mismo tiempo, `UpdateVehiculoRequest` lo exige. El contrato pide un dato que no devuelve.

**Arreglo (las dos puntas, en este orden).**

1. **Backend:** agregar `fechaUltimoMantenimiento` a `VehiculoResponse`. Sin eso el cliente
   no puede hacer nada correcto.
2. **Front:** leerlo en `formFrom` en vez de inventar `todayISO()`. `todayISO()` queda solo
   en `emptyForm()`, que es donde tiene sentido (alta de un vehículo nuevo).

**Mitigación inmediata, si el cambio de backend no entra ya.** Hoy la peor variante es que
el campo *aparenta* mostrar el dato guardado. Mientras el backend no lo devuelva, dejar el
input vacío en modo edición y rotularlo *"dejalo vacío para no modificar la fecha
registrada"* — y que el backend trate el null como "no tocar". Es feo, pero deja de mentir.

---

# ALTAS

## [x] UI-00 — Un registro puede terminar con la cuenta creada y el usuario sin saber su username

> **Resuelto.** El `login(res)` corre antes del `Alert`, que quedó como aviso con
> `cancelable: false`. La sesión ya no depende de que alguien toque un botón.
>
> **Un matiz que apareció al implementarlo:** mover el `login` dentro del `try` del registro
> creaba un pozo nuevo — si `login` falla (escritura a SecureStore), el `catch` habría dicho
> *"No se pudo crear la cuenta"* con la cuenta YA creada, mandando al usuario a reintentar
> con un documento tomado. El `login` va en su propio `try` interno: pase lo que pase con la
> sesión, el Alert con el username se muestra igual.
>
> Queda pendiente el complemento sugerido (botón de copiar el username en Perfil). El dato
> ya es visible ahí (`ProfileView` lo pinta desde `user.username`), así que no es urgente.

**Dónde:** `app/(auth)/register.tsx:80-92`.

**Qué pasa.** El username lo genera el backend a partir de nombre + apellido; el usuario no
lo elige y **es la única vez que lo ve**. La app se lo muestra en un `Alert`, y hace el
auto-login en el `onPress` del botón:

```ts
Alert.alert(
  '¡Registro exitoso!',
  `Tu usuario es: ${res.username}\nGuardalo para iniciar sesión.`,
  [{ text: 'Continuar', onPress: () => { void login(res); } }],
);
```

En Android, `Alert.alert` es **cancelable por defecto**: se cierra con el botón atrás o
tocando afuera, y en ese caso `onPress` **nunca corre**.

**Consecuencia.** La cuenta quedó creada en el backend. El token que vino en el
`AuthResponse` se descarta sin persistirse. El usuario queda en la pantalla de registro,
sin sesión, **sin conocer su username** y sin ninguna forma de recuperarlo desde la app
(no hay "olvidé mi usuario", y `¿Olvidaste tu contraseña?` no es ni un botón — ver
`UI-05`). Y si intenta registrarse de nuevo, el documento ya está usado. Queda trabado:
necesita que el admin le lea el username de la base.

**Agrava:** el `finally { setLoading(false) }` corre apenas se dispara el Alert, así que el
botón "Crear cuenta" se rehabilita detrás del modal.

**Arreglo.** Hacer el `login(res)` **antes** de mostrar el Alert, no dentro de su callback:
la sesión no debería depender de que alguien toque un botón. El Alert queda solo como
aviso informativo (`cancelable: false` de todos modos), y el username, una vez logueado, ya
es visible en Perfil. Complemento: mostrar el username también en Perfil con un botón de
copiar, así deja de ser un dato de una sola oportunidad.

---

## [~] STATE-01 — `useFetch` no cancela ni descarta respuestas viejas: condición de carrera en cada filtro

> **Resuelto en la corrección, parcial en la red.** `useFetch` numera cada ejecución y solo
> la más reciente escribe estado; también descarta si el componente se desmontó. Arregla a
> los **8** consumidores de una (el hallazgo decía 6: hoy son 8). El contrato de `deps` quedó
> documentado en el JSDoc del hook.
>
> **Queda pendiente el `AbortController` real.** Esto descarta la respuesta, no corta la
> conexión: sobre la red del yacimiento se siguen bajando bytes que no se usan. Cortarla de
> verdad exige pasar un `signal` por `api.ts` y por cada función de `services/`, y por los 8
> consumidores — un refactor ancho sobre código sin tests. Va después de `BLD-00`.
>
> Corrección menor al hallazgo: el warning de setState sobre un componente desmontado que
> menciona más abajo ya no existe — React 19 lo removió. La guarda se mantiene igual, pero
> por no hacer trabajo al pedo, no por el warning.

**Dónde:** `hooks/useFetch.ts:14-30`. Impacta a **los seis consumidores** del hook.

**Qué pasa.** El hook no tiene `AbortController` ni bandera de "ignorar esta respuesta":

```ts
const run = useCallback(async () => {
  setLoading(true);
  setError(null);
  try {
    setData(await fn());        // ← escribe siempre, sea o no la respuesta más reciente
  } catch (e) { /* ... */ }
  finally { setLoading(false); }
}, deps);

useEffect(() => { run(); }, [run]);
```

Cuando cambian las deps, sale un request nuevo **sin cancelar el anterior**, y el que
conteste último gana — que no es necesariamente el último pedido.

**Dónde muerde concretamente.** En Analítica
(`app/(administrador)/index.tsx:216-219`), el filtro multi-select de usuarios dispara un
`getStats` **por cada empleado que se tilda**:

```ts
const { data: stats, ... } = useFetch(
  () => getStats(range, { fecha, vehiculoId, empleadoIds }),
  [range, fecha, vehiculoId, empleadoIds],
);
```

Tildar cuatro operarios son cuatro requests en vuelo. Si el de tres empleados vuelve
después del de cuatro, **la pantalla muestra los números de tres mientras el desplegable
dice cuatro.** No hay error, no hay spinner: hay un número de gasto equivocado presentado
como correcto, en la pantalla que existe justamente para decidir sobre plata.

Sobre una conexión buena casi nunca pasa. Sobre la conexión de un yacimiento, pasa.

**Agrava:** el multi-select tiene un botón "Listo", pero el request no espera a que lo
toquen — sale en cada tilde. Ver `PERF-00`, que es el mismo problema en otra pantalla.

**Arreglo.** En `useFetch`, la versión mínima que resuelve la corrección:

```ts
useEffect(() => {
  let vigente = true;
  (async () => {
    setLoading(true); setError(null);
    try { const r = await fn(); if (vigente) setData(r); }
    catch (e) { if (vigente) setError(/* ... */); }
    finally { if (vigente) setLoading(false); }
  })();
  return () => { vigente = false; };
}, [run]);
```

Esto además arregla el warning de setState sobre un componente desmontado cuando el
operario cambia de pestaña con un request en vuelo. La versión completa pasa un `signal` de
`AbortController` hasta `api.ts` para cortar la conexión, no solo ignorar la respuesta —
sobre red mala eso también ahorra datos.

**Nota sobre las deps.** El `// eslint-disable-next-line react-hooks/exhaustive-deps` de la
línea 25 esconde que `fn` no está en las dependencias. Hoy todos los consumidores listan
correctamente lo que `fn` captura, así que no hay bug — pero es una convención que se
sostiene sola, sin nadie que la verifique (ver `BLD-00`: no hay linter que corra esa
regla). Documentar el contrato en el JSDoc del hook: *"`fn` se re-captura solo cuando
cambia `deps`; todo lo que `fn` lea de afuera tiene que estar en `deps`."*

---

## [x] PERF-00 — `TicketsABM`: un request por tecla en el filtro de monto, sin debounce ni guarda de carrera

> **Resuelto.** Debounce de 350 ms sobre monto mín/máx antes de que entren a `filtros`, y la
> misma guarda de generación de `useFetch` dentro de `cargar`. Los desplegables siguen
> disparando al instante: son selecciones discretas, no tecleo.
>
> **No** se migró `TicketsABM` al `useFetch` compartido: siguen existiendo dos
> implementaciones de paginación (esta y la de `historial.tsx`). Decisión explícita — el
> hook no sabe paginar y reescribir la carga del ABM sin tests era más riesgo que el que
> resuelve. Candidato a unificar después de `BLD-00`, junto con los dos mapeadores
> `Ticket → Row` de `DATA-00`.

**Dónde:** `components/admin/TicketsABM.tsx:59-103`.

**Qué pasa.** Los inputs de monto mínimo y máximo entran directo al `useMemo` de filtros,
que es dependencia del `useCallback` de carga, que es dependencia del efecto que dispara la
consulta:

```
montoMin (cada tecla) → filtros → cargar → useEffect → GET /admin/tickets
```

Escribir `150000` son **seis requests** a `/admin/tickets`: `1`, `15`, `150`, `1500`,
`15000`, `150000`. Cada uno es una consulta paginada contra la tabla que más crece del
sistema (ver `DB-03` en la auditoría del backend).

**Y como `cargar` tampoco descarta respuestas viejas** (mismo problema que `STATE-01`, pero
hecho a mano acá), la respuesta de `15000` puede llegar después de la de `150000`: **la
lista termina mostrando los tickets de un filtro que no es el que dice la pantalla.**

**Consecuencia.** Carga inútil sobre el backend y sobre la conexión del admin, y un
resultado que puede no corresponder al filtro visible. En una pantalla de rendición de
gastos, un listado que no coincide con su filtro es peor que un error.

**Arreglo.** Debounce de 300-400 ms sobre `montoMin`/`montoMax` antes de que entren a
`filtros` (los desplegables de operario y vehículo pueden seguir disparando al instante:
son selecciones discretas, no tecleo), más el mismo guard de `vigente` de `STATE-01` en
`cargar`. Lo ideal es extraer ambas cosas al `useFetch` compartido en vez de mantener dos
implementaciones de paginación con los mismos dos bugs.

---

## [ ] A11Y-00 — 67 controles táctiles, cero props de accesibilidad, y 24 de ellos son solo un glifo

**Dónde:** todo el proyecto. Verificado: `rg "accessibilityLabel|accessibilityRole|accessibilityHint|accessible="` sobre `app/`, `components/`, `context/` y `hooks/` devuelve **cero resultados** en 6.336 líneas.

**Qué pasa.** Hay **67 `Pressable`** en 14 archivos. Ninguno declara `accessibilityRole`,
`accessibilityLabel`, `accessibilityHint` ni `accessibilityState`. **24 de ellos no
contienen más texto que un carácter** — es literalmente todo lo que puede anunciar un lector
de pantalla:

| Control | Se anuncia como | Debería decir |
| --- | --- | --- |
| `✏️` (editar vehículo) | "emoji lápiz" | "Editar Toyota Hilux SRV" |
| `🗑️` (dar de baja) | "emoji tacho" | "Dar de baja Toyota Hilux SRV" |
| `✕` (anular ticket) | "equis" | "Anular ticket de 58 litros" |
| `✎` / `✓` (editar perfil) | un glifo | "Editar teléfono" / "Confirmar teléfono" |
| `‹` / `›` (navegar período) | un glifo | "Período anterior" / "Período siguiente" |
| `?` (abrir tutorial) | "signo de pregunta" | "Ver tutorial" |
| `▾` (`FilterDropdown`) | un glifo | "Filtrar por vehículo, actualmente Todos" |

Y no es solo el lector de pantalla: **la barra de tabs custom**
(`app/(empleado)/_layout.tsx:52-57`) —la navegación principal de toda el área de operario—
no declara `accessibilityRole="tab"` ni `accessibilityState={{ selected: focused }}`. Para
TalkBack no hay tabs: hay tres cosas tocables sin relación entre sí y sin forma de saber
cuál está activa.

**Consecuencia.** La app es inoperable con lector de pantalla. Los botones destructivos
—dar de baja un vehículo, anular un ticket contable— son los que peor se anuncian, que es
exactamente al revés de como debería ser.

**Contexto que agrava.** Es una app de trabajo, para uso obligatorio en obra. Un operario
con baja visión no puede elegir no usarla. El costo de arreglarlo es bajísimo: un
`accessibilityRole` y un `accessibilityLabel` por control.

**Arreglo.** Empezar por lo destructivo y por la navegación:

1. Los tabs de `_layout.tsx` (`role="tab"` + `state.selected`).
2. Los 24 controles de glifo, con label que incluya **sobre qué** actúa.
3. `accessibilityState={{ disabled }}` en los que se deshabilitan (`OptionChips`, flechas de
   período, cards en mantenimiento) — hoy se comunica solo con `opacity`.

Como no hay linter (`BLD-00`), esto se va a volver a llenar. Al configurar ESLint, incluir
`eslint-plugin-react-native-a11y` para que la regresión la marque la herramienta.

---

## [ ] UI-01 — La pantalla de Perfil finge que guarda datos que no guarda

**Dónde:** `components/fuel/ProfileView.tsx`, pantalla compartida entre operario y admin.

**Qué pasa.** La pantalla presenta cuatro cosas que no son reales:

**1. La edición no persiste (líneas 32-42 y 76-90).** Cada campo tiene su lápiz ✎, abre un
`TextInput`, se escribe, se toca ✓ y el valor cambia en pantalla. No hay ningún request: es
`useState` local. Al salir de la pestaña y volver, el dato viejo está de vuelta. El propio
comentario lo admite —*"Edición local (mock): sin persistencia todavía"*— pero **la UI no
lo admite**: no dice "próximamente", no está deshabilitada, no avisa nada. El usuario cree
que corrigió su teléfono.

Esto está en producción desde la v1.6.0.

**2. El switch de notificaciones no controla nada (líneas 96-108).** Es `useState(true)`
contra nada. **`expo-notifications` no está en `package.json`**: no hay sistema de
notificaciones que activar o desactivar.

**3. "✓ Cuenta verificada" está hardcodeado (línea 63).** Se muestra siempre, para todos, sin
consultar ningún estado. No existe el concepto de verificación en el modelo.

**4. "Obra Ruta 33 Sur" está hardcodeado (línea 61).** Un dato de obra inventado, pegado al
rol de todos los usuarios. No hay entidad "obra" en el sistema.

**Consecuencia.** Un usuario cambia su teléfono, ve el cambio, cierra la app. El teléfono
sigue viejo en la base y él no lo sabe. Cuando el admin lo llame al número viejo, ninguno
de los dos va a entender qué pasó. En una app de gestión, una UI que miente sobre lo que
guardó es peor que una UI que no ofrece la función.

**Arreglo — ordenado por costo.**

- **Barato y honesto (hoy):** sacar los lápices y el switch, mostrar los datos como
  read-only, y `Constants.expoConfig.version` abajo (eso ya está bien resuelto). Sacar
  "Cuenta verificada" y la obra inventada, o reemplazar la obra por un dato real.
- **Completo (cuando haya endpoint):** cablear la edición a un `PUT /me`, con estado de
  guardado, error y reversión. Notificaciones solo cuando exista el sistema detrás.

**Nota de implementación.** El estado de los campos se inicializa con
`useState(() => ...)`, así que **no se re-sincroniza si `user` cambia** después del montaje.
Al cablear la persistencia, esa inicialización perezosa se va a comer las
actualizaciones — hay que resolverlo con `key={user.username}` o derivando el valor
directo de `user`, no copiándolo a estado.

---

## [ ] BLD-00 — Sin ESLint, sin tests, sin Prettier — y hay un `eslint-disable` para una regla que nadie corre

**Dónde:** raíz de `mobile/`.

**Qué falta.**

| Herramienta | Estado |
| --- | --- |
| ESLint | **No hay configuración.** Ni `.eslintrc*` ni `eslint.config.*` |
| Tests | **Cero archivos.** Ni `.test.tsx` ni `.spec.ts`, ni runner en `package.json` |
| Prettier | Sin configuración |
| CI | Sin workflow para `mobile/` |
| Scripts | `package.json` tiene `start`, `android`, `ios`, `web`. No hay `lint`, `test` ni `typecheck` |

**Lo que mejor lo retrata.** `hooks/useFetch.ts:25` tiene:

```ts
// eslint-disable-next-line react-hooks/exhaustive-deps
```

**Se está suprimiendo una regla que ninguna herramienta ejecuta.** El comentario es
decorativo: documenta la intención de silenciar un linter que no existe. Y `exhaustive-deps`
es precisamente la regla que habría marcado `STATE-01` y buena parte de `STATE-02`.

**Consecuencia.** No hay nada entre un error y producción salvo la lectura de una persona.
`tsc` es la única red que hay hoy, y es real (`DATA-00` no es un error de tipos: el código
compila perfecto y el número está mal). Todos los hallazgos de esta auditoría se
encontraron leyendo. Eso no escala y no se repite solo.

**Contraste con el backend.** `BLD-01` de `BACKEND-AUDIT.md` (JaCoCo, análisis estático,
chequeo de dependencias) ya está resuelto y el backend está en 304 tests y 78,1% de
cobertura. El front está en cero. La misma app, dos estándares distintos.

**Arreglo, en orden de retorno por esfuerzo.**

1. **ESLint** con `eslint-config-expo`, `react-hooks` (con `exhaustive-deps` en `error`, no
   `warn`) y `eslint-plugin-react-native-a11y` para `A11Y-00`. Es media hora y marca
   sola varias cosas de esta lista.
2. **Scripts `lint` y `typecheck`** en `package.json`, y un workflow de CI que los corra
   sobre `mobile/` en cada PR.
3. **Tests**, empezando por donde hay lógica pura y consecuencias caras:
   `constants/labels.ts` (`formatDay` y su off-by-one de zona horaria, `formatMoney`),
   `services/vehiculos.ts` (`consumoParaStats`, `tituloVehiculo`), el mapeador unificado de
   `DATA-00` y `hooks/useFetch.ts` (carrera y cancelación). Con `jest-expo` y
   `@testing-library/react-native`.
4. **Prettier**, para cerrar la deriva de formato (ver `UI-08`: hay JSX de más de 200
   caracteres en una línea conviviendo con bloques prolijos en el mismo archivo).

---

# MEDIAS

## [ ] PERF-01 — La flota del operario se pinta con `ScrollView` + `.map()`: nada se virtualiza

**Dónde:** `app/(empleado)/index.tsx:281-338`; el mismo patrón en
`app/(administrador)/index.tsx` (flota, empleados, habilitados) y en `TicketsABM`.

**Qué pasa.** El catálogo completo de vehículos y herramientas se renderiza de una:

```tsx
<ScrollView ...>
  {filtrados.map((v) => <VehiculoCard key={v.id} item={v} onOpen={openScan} />)}
  {filtradasHerramientas.map((h) => <HerramientaCard ... />)}
</ScrollView>
```

Un `ScrollView` **monta todos sus hijos**, estén o no en pantalla. Cada `VehiculoCard` es un
ícono SVG, un `Badge`, cuatro `Text` y tres `View`. Con 15 vehículos no se nota; con 200 son
~2.000 vistas nativas montadas de golpe: arranque lento, memoria alta y scroll con tirones
en un teléfono de gama baja.

En el proyecto **ya está la solución escrita**: `app/(empleado)/historial.tsx` usa `FlatList`
con `keyExtractor`, `renderItem` memoizado y paginación. La pantalla más importante para el
operario es la que no la usa.

**Arreglo.** Pasar la flota a `FlatList` (o `SectionList`, que encaja mejor con las dos
secciones Vehículos/Herramientas), con el buscador y los filtros en `ListHeaderComponent`.
Agregar `removeClippedSubviews`, `maxToRenderPerBatch` y `windowSize`. En `TicketsABM` es
menos urgente porque pagina de a 20, pero conviene por consistencia.

---

## [ ] PERF-02 — `memo` neutralizado: las props nunca son estables

**Dónde:** `app/(empleado)/index.tsx:89` y `:135`.

**Qué pasa.** `VehiculoCard` y `HerramientaCard` están envueltas en `memo`. Pero reciben:

```tsx
const openScan = (id: number) => { router.push(/* ... */); };          // línea 197
// ...
<VehiculoCard key={v.id} item={v} onOpen={openScan} />                 // línea 318
```

`openScan` **se recrea en cada render**. `memo` compara props por identidad, `onOpen` siempre
es distinta, la comparación siempre falla y **el componente re-renderiza siempre**. El `memo`
no ahorra nada: solo agrega una comparación que nunca da positivo.

Como el buscador es estado del padre, esto se paga en cada tecla: escribir "hilux" son cinco
re-renders de la lista entera.

**Arreglo.** `useCallback` en `openScan` y `openScanHerramienta` (`router` es estable, así
que las deps quedan vacías). Con eso `memo` empieza a hacer su trabajo. El contraste está en
`historial.tsx:158`, donde `onPress={setSelected}` es un setter de React —estable por
definición— y por eso ahí el `memo` sí funciona.

**Menor, del mismo archivo:** `operativos` y `enTaller` (líneas 237-238) se recalculan en
cada render con dos `.filter().length` mientras sus vecinos `activos` y `filtrados` sí están
memoizados. Es barato, pero es inconsistencia.

---

## [ ] NAV-00 — El botón atrás de Android sale de la pantalla de carga y se lleva el formulario

**Dónde:** `app/(empleado)/escanear.tsx`, etapas `capture` y `analyzing`.

**Qué pasa.** Cámara y análisis **no son rutas: son estado interno** (`stage`). Para el
sistema de navegación no existen. Entonces el botón atrás de Android no las cierra: **hace
pop de la ruta `escanear` completa.**

**El escenario:** el operario completa litros, lectura del odómetro y proveedor; toca
"Adjuntar foto"; se abre la cámara; el encuadre no le gusta y aprieta atrás por reflejo —
que es el gesto natural en Android para "salir de esto". **Sale de la pantalla de carga
entera y pierde todo lo tipeado.** En la etapa `analyzing` es peor: el OCR sigue corriendo
contra un componente que se está desmontando.

Hay una ✕ para volver al formulario, pero compite contra el gesto que el sistema entrenó
durante quince años.

**Arreglo.** `useEffect` con `BackHandler.addEventListener('hardwareBackPress', ...)` que,
cuando `stage !== 'form'`, haga `setStage('form')` y devuelva `true` para consumir el
evento. Es la MUST DO de "manejar el botón atrás de Android" de la skill de React Native.

**Complemento:** si el formulario tiene datos cargados, confirmar antes de abandonar la
pantalla (`beforeRemove` de la navegación). Perder una carga tipeada en obra significa
volver a sacar el ticket del bolsillo con las manos sucias.

---

## [ ] DEAD-00 — `constants/Colors.ts` sin usar, y ~85% de `data/mock.ts` es código muerto

**Dónde:** `constants/Colors.ts` (11 líneas), `data/mock.ts` (127 líneas).

**`constants/Colors.ts`: cero referencias.** Es la paleta clara del prototipo original. La
única mención en todo el repo es un comentario en `constants/theme.ts:4` que dice que existe.
La app entera es oscura y toma de `theme.ts`. Borrar el archivo y ajustar el comentario.

**`data/mock.ts`: dos de trece exports se usan.** Verificado símbolo por símbolo:

| Export | Uso |
| --- | --- |
| `TUTORIAL_STEPS` | `TutorialContext`, `TutorialOverlay` |
| `PROFILE_FIELDS` | `ProfileView` — **y es un problema, ver `UI-02`** |
| `MACHINERY`, `updateMachinery`, `Machine` | **muerto** |
| `HISTORY`, `Load` | **muerto** |
| `SCAN_DEFAULTS` | **muerto** (OCR simulado, ya hay OCR real) |
| `kpis`, `vehicleData`, `providerData`, `userData` | **muerto** (analítica simulada, ya hay `/admin/stats`) |
| `rangeLabel`, `fmt`, `Range` | **muerto** |

**Lo más feo de lo muerto:**

```ts
export let MACHINERY: Machine[] = [ /* ... */ ];        // ← `let` exportado, mutable
export const updateMachinery = (newMachinery: Machine[]) => { MACHINERY = newMachinery; };
```

Estado global mutable reasignable desde cualquier import. No lo usa nadie, pero está a mano
para que alguien lo use. Y `Machine.icon` y `Load.icon` son `any` — los dos únicos `any` del
proyecto están acá.

**Además:** `fmt` (línea 83) es una copia byte a byte de `formatMoney` de
`constants/labels.ts:64`.

**Consecuencia.** El archivo se llama `mock.ts` y su comentario de cabecera dice que es el
"seam donde se enchufa la API real". Ya se enchufó: hay servicios reales para todo esto. Lo
que quedó es andamio después de terminada la obra, y confunde a quien llegue: da a entender
que hay pantallas todavía sin cablear.

**Arreglo.** Borrar los once exports muertos. Mover `TUTORIAL_STEPS` a
`constants/tutorial.ts` (es copy de producto, no un mock) y resolver `PROFILE_FIELDS` con
`UI-02`. Después de eso, `data/mock.ts` desaparece.

---

## [ ] UI-02 — Un mock maneja la pantalla de perfil real, acoplado por índice de array

**Dónde:** `components/fuel/ProfileView.tsx:32-42`.

**Qué pasa.** El perfil real de un usuario logueado se arma **encima de `PROFILE_FIELDS`,
que es un mock con los datos de "Juan Pérez"**, machacando los valores por posición:

```ts
const [fields, setFields] = useState(() =>
  PROFILE_FIELDS.map((f, i) => {
    if (!user) return f;
    if (i === 0) return { ...f, value: fullName };
    if (i === 1) return { ...f, value: user.username };
    if (i === 2) return { ...f, value: user.telefono ?? 'No cargado' };
    return f;
  }),
);
```

Del mock queda solo la etiqueta ("Nombre completo", "Usuario", "Teléfono"); el valor se pisa.
Pero el vínculo es **el índice**: si alguien reordena `PROFILE_FIELDS` o inserta un campo, el
teléfono del usuario aparece rotulado "Usuario" y nadie se entera hasta verlo en pantalla.
TypeScript no puede marcar esto: los tipos son idénticos.

**Y el fallback deja escapar al mock.** Con `user == null`, `PROFILE_FIELDS` se devuelve tal
cual: la pantalla renderiza **"Juan Pérez", "juan.perez", "+54 9 342 555-1234"** como si
fueran datos reales. Se refuerza arriba, en las líneas 22-26, con `'Juan Pérez'` y `'JP'`
como valores por defecto de nombre e iniciales.

**Arreglo.** Derivar la lista del usuario, no de un mock, y sin índices:

```ts
const fields = user
  ? [
      { key: 'nombre',   label: 'Nombre completo', value: `${user.nombre} ${user.apellido}` },
      { key: 'username', label: 'Usuario',         value: user.username },
      { key: 'telefono', label: 'Teléfono',        value: user.telefono ?? 'No cargado' },
    ]
  : [];
```

Sin `user` no hay perfil que mostrar: la pantalla vive detrás del guard de autenticación, así
que el caso no debería existir — y si existiera, un estado vacío es la respuesta honesta, no
"Juan Pérez". Cierra `DEAD-00` y prepara el terreno para `UI-01`.

---

## [ ] DATA-02 — `replace(',', '.')` rompe con el separador de miles argentino

**Dónde:** `app/(empleado)/escanear.tsx:233` y `:250`,
`app/(administrador)/index.tsx:534`, `components/admin/TicketsABM.tsx:283`.

**Qué pasa.** Todos los campos numéricos con decimales normalizan así:

```ts
const litrosNum = parseFloat(litros.replace(',', '.')) || 0;
```

`String.replace` con un string **reemplaza solo la primera ocurrencia**, y no saca los puntos
de miles. Con la convención argentina —punto para miles, coma para decimales— la conversión
falla:

| Se tipea | Se pretende | `parseFloat` devuelve |
| --- | --- | --- |
| `58,5` | 58,5 L | `58.5` ✅ |
| `1.234` | 1.234 L | **`1.234`** ❌ |
| `1.234,5` | 1.234,5 L | **`1.234`** ❌ |

**Consecuencia.** Una carga de camión de `1.234` litros se registra como **1,234 litros**.
Con `keyboardType="numeric"` el teclado ofrece el punto, así que escribirlo es natural. Y no
hay validación de rango que lo frene: 1,234 litros es un número válido, solo que absurdo.

Impacta litros, precio por litro, consumo promedio (alta de vehículo) y los filtros de monto
del panel.

**Arreglo.** Una única función de parseo compartida en `constants/labels.ts` (al lado de
`formatMoney`, que ya sabe de `es-AR`), que saque los separadores de miles y normalice la
coma decimal. Reemplazar las cuatro copias. Complemento: rango razonable por campo
(un vehículo no carga 1,2 litros ni 90.000) para avisar antes de mandarlo.

**Relacionado:** `escanear.tsx:276` usa `parseInt(usoAcumulado, 10)` para la lectura del
contador. `parseInt` **trunca decimales y acepta basura al final**: `"1520.5"` → `1520`,
`"152340km"` → `152340` sin chistar. Un horómetro marca horas con decimales, así que ahí se
pierde precisión real.

---

## [ ] UI-03 — La misma entidad se valida distinto según por dónde entre

**Dónde:** `app/(auth)/register.tsx:56-67` contra
`app/(administrador)/index.tsx:917-930`.

**Qué pasa.** Un empleado se puede crear por dos caminos, con dos criterios distintos:

| Validación | Alta del admin | Registro público |
| --- | --- | --- |
| Documento con formato | `DOCUMENTO_REGEX = /^\d{7,9}$/` | **solo que no esté vacío** |
| Largo mínimo de contraseña | `>= 8` | **ninguno** |
| Campos obligatorios | ✅ | ✅ |

El camino **público** —el que usa cualquier persona con la URL de la API— es el menos
exigente. Un documento `"1"` o una contraseña de un carácter pasan la validación del
cliente y se van al backend.

**Consecuencia.** No es un agujero de seguridad: el backend valida (por eso está el padrón
de habilitados). Es una inconsistencia de producto y un ida y vuelta evitable — el operario
en obra, con conexión mala, se entera del rechazo después del round-trip en vez de al
instante. Y a nivel código son dos verdades sobre las mismas reglas, en dos archivos, que
ya divergieron una vez.

**Arreglo.** Subir las reglas a un módulo compartido (`constants/validation.ts` o
`services/empleados.ts`, al lado del payload que describen) y consumirlo desde los dos
formularios. `DOCUMENTO_REGEX` ya existe: hoy vive suelto en el archivo del panel
(`app/(administrador)/index.tsx:816`) y el registro no puede importarlo sin arrastrarse la
pantalla entera.

---

## [ ] UI-04 — La fecha de mantenimiento se carga como texto libre, sin validación ni selector

**Dónde:** `app/(administrador)/index.tsx:687-688`.

**Qué pasa.**

```tsx
<Text style={styles.fieldHint}>Último mantenimiento (AAAA-MM-DD)</Text>
<TextInput style={styles.abmInput} value={form.fechaUltimoMantenimiento}
           onChangeText={(t) => setForm({ ...form, fechaUltimoMantenimiento: t })} />
```

Sin `keyboardType`, sin máscara, sin validación de formato, sin verificar que sea una fecha
que existe. `save()` valida capacidad, uso y consumo — la fecha no se toca. Lo que se tipeó
viaja tal cual.

**Consecuencia.** `2026-13-45` se manda al backend. `05/08/2026` (el formato que un argentino
escribe por reflejo) también. En el mejor caso vuelve un 400 sin contexto; en el peor entra
una fecha inválida. No hay librería de fechas en `package.json`: no hay `@react-native-community/datetimepicker` ni equivalente.

**Arreglo.** Un date picker nativo (`expo` trae integración para
`@react-native-community/datetimepicker`), que además elimina de raíz la ambigüedad de
formato. Como paso intermedio: validar el patrón y que la fecha exista antes de enviar, y
`keyboardType="numbers-and-punctuation"`.

**Depende de `DATA-01`:** mientras el backend no devuelva `fechaUltimoMantenimiento`, este
campo miente sobre lo que hay guardado. Arreglar los dos juntos.

---

# BAJAS

## [ ] UI-05 — "¿Olvidaste tu contraseña?" no es un botón

**Dónde:** `app/(auth)/login.tsx:131`.

```tsx
<Text style={styles.forgot}>¿Olvidaste tu contraseña?</Text>
```

Un `<Text>` suelto: no es `Pressable`, no navega, no hace nada. Está centrado debajo del CTA,
en el lugar exacto donde va ese link en todos los logins del mundo, así que el usuario lo
toca y no pasa nada. No hay flujo de recuperación en el backend ni pantalla en el front.

Pega fuerte junto a `UI-00`: si el registro se cortó y el usuario no sabe su username, éste
es el lugar al que va a ir a buscar ayuda.

**Arreglo.** Sacarlo, o reemplazarlo por lo que sí se puede hacer hoy: *"Si no podés
ingresar, pedile al administrador que restablezca tu contraseña."* Que es la verdad.

---

## [ ] UI-06 — El tutorial promete un cambio de idioma que no existe

**Dónde:** `data/mock.ts:64`.

```
'Paso 4 de 4' → 'Desde tu perfil editás tus datos, cambiás el idioma y cerrás sesión.'
```

**No hay i18n en el proyecto.** Verificado: ni librería, ni archivos de traducción, ni
selector de idioma en `ProfileView`. Todo el texto está hardcodeado en español.

De las tres cosas que promete el paso, **dos son falsas**: el idioma no existe y editar los
datos no persiste (`UI-01`). La única real es cerrar sesión.

**Arreglo.** Reescribir el paso 4 con lo que la pantalla hace de verdad. Va junto con `UI-01`
y con el traslado de `TUTORIAL_STEPS` fuera de `mock.ts` (`DEAD-00`).

---

## [ ] UI-07 — El historial dice "Todavía no registraste cargas" cuando en realidad el filtro no matcheó

**Dónde:** `app/(empleado)/historial.tsx:223`.

```tsx
ListEmptyComponent={<EmptyState message="Todavía no registraste cargas." />}
```

Un solo mensaje para dos situaciones distintas. Con el filtro "Esta semana" puesto y sin
cargas en los últimos siete días, el operario —que cargó cuarenta tickets el mes pasado— lee
que nunca registró nada.

Se agrava porque el filtrado es en cliente sobre las páginas ya traídas: si la primera página
de 30 no tiene nada del rango, la lista queda vacía **y no hay nada que scrollear**, así que
`onEndReached` no se dispara y no llegan más páginas. El operario no tiene forma de llegar a
sus datos desde esa pantalla.

**Arreglo mínimo.** Distinguir los dos casos, como ya hace bien la pantalla de flota
(*"No hay vehículos que coincidan con los filtros"*):

```tsx
ListEmptyComponent={
  <EmptyState message={
    activeFilter === 'Todos'
      ? 'Todavía no registraste cargas.'
      : 'No hay cargas en este período.'
  } />
}
```

**Arreglo de fondo.** Mandar el filtro de fecha al backend como parámetro de
`GET /tickets/me`, en vez de filtrar en cliente sobre páginas parciales. Es la única forma
de que "Este mes" signifique el mes y no "el mes, dentro de lo que bajé hasta ahora".

---

## [ ] UI-08 — 41 colores escritos a mano; `#1F2226` es `colors.surface` catorce veces

**Dónde:** todo `app/` y `components/`.

**41 literales hexadecimales** fuera de `constants/`, y la mayoría **ya tiene su token**:

| Literal | Veces | Token que existe |
| --- | --- | --- |
| `#1F2226` | 14 | `colors.surface` |
| `#1b1d20` | 6 | (sin token — habría que crearlo) |
| `#4d525a` | 3 | (sin token) |
| `#C9CDD2`, `#151719`, `#22262b`, `#16181B` | 2 y 1 | `#22262b` = `colors.surfaceAlt`, `#16181B` = `colors.bg` |
| `#F5C518`, `#ffd94d`, `#c99a00` | 1 c/u | `colors.primary`, `primaryLight`, `primaryDark` |

El caso más claro es `CHART_COLORS` en `app/(administrador)/index.tsx:69`: cinco hexadecimales
de los cuales tres son la rampa de amarillos del tema, copiada.

**Consecuencia.** El tema deja de ser la fuente de verdad. Cambiar `colors.surface` retoca
una parte de las superficies y deja el resto en el valor viejo — el tipo de bug visual que
aparece a mitad de scroll y cuesta más encontrar que arreglar.

**Arreglo.** Reemplazar por tokens donde ya existen; para los seis o siete que no tienen
(`#1b1d20` de los botones secundarios, `#4d525a` de los textos muy tenues), agregarlos a
`theme.ts` con un nombre que diga su rol. Al configurar ESLint (`BLD-00`),
`react-native/no-color-literals` deja el archivo cerrado.

---

## [ ] DEAD-01 — 16 claves de estilo duplicadas byte a byte entre login y register

**Dónde:** `app/(auth)/login.tsx:141-206` y `app/(auth)/register.tsx:170-224`.

**Qué pasa.** Los dos `StyleSheet.create` comparten **16 claves idénticas**: `safe`, `flex`,
`content`, `brandRow`, `subtitle`, `tabs`, `tab`, `tabActive`, `tabText`, `tabTextActive`,
`fieldLabel`, `input`, `error`, `cta`, `ctaDisabled`, `ctaText`. Mismos valores exactos, ~90
líneas duplicadas.

Y no es solo el estilo: el bloque de marca (`Logo` + `Wordmark` + subtítulo) y el conmutador
de pestañas Iniciar sesión/Registrarse están escritos dos veces con la selección invertida.

`register.tsx` además tiene componentes `Field` e `Input` locales
(líneas 152-176) que resuelven exactamente lo que `login.tsx` escribe a mano. Existe la
abstracción; vive del lado equivocado del archivo.

**Consecuencia.** Cualquier retoque de la marca hay que hacerlo dos veces, y la segunda es la
que se olvida. En `login.tsx` ya hay un `#242a30` suelto (`hintIcon`) que no está en
`register`: la deriva ya empezó.

**Arreglo.** Un `components/auth/AuthShell.tsx` con la marca, el subtítulo, el conmutador y
los estilos compartidos, más `Field` e `Input` promovidos a `components/ui/`. Las dos
pantallas quedan solo con sus campos y su submit.

---

## [ ] DEAD-02 — Comentario huérfano pegado a la función equivocada

**Dónde:** `services/vehiculos.ts:71-80`.

```ts
// Etiqueta del campo donde el empleado anota la lectura al cargar combustible.
// Nombra el instrumento a propósito: en el campo se lee un horómetro o un
// odómetro, y decirlo así evita que alguien anote kilómetros en una máquina.
// Sufijo con el que se muestra un consumo ya calculado, a partir de la unidad
// que mandó el servidor. Es la forma preferida: no necesita el vehículo entero,
// solo el `unidadUso` que viaja en la respuesta.
export function sufijoConsumo(unidad: UnidadUso): string {
```

Son **dos comentarios pegados**. Las primeras tres líneas documentan `etiquetaLectura`, que
está 34 líneas más abajo (línea 110) y quedó sin comentario. Un movimiento de código dejó el
comentario atrás y ahora describe una función que no es.

Es de una línea de arreglo, pero justo en el archivo cuyos comentarios son de los mejores del
proyecto: un comentario que miente vale menos que ninguno.

**Arreglo.** Mover las tres primeras líneas a `etiquetaLectura`.

**Además, del mismo archivo:** el orden está desordenado. `tituloVehiculo` y
`textoBusquedaVehiculo` usan la interfaz `Vehiculo`, que se declara 100 líneas más abajo, y
el tipo `TipoCombustible` aparece después de las funciones que dependen de él. El hoisting de
TypeScript lo permite, pero se lee al revés. Agrupar: tipos → helpers de presentación →
llamadas a la API.

---

## [ ] PERF-03 — La foto del ticket se sube sin comprimir

**Dónde:** `app/(empleado)/escanear.tsx:190` y `:200`.

```ts
await cameraRef.current?.takePictureAsync({ quality: 0.6, skipProcessing: true });
await ImagePicker.launchImageLibraryAsync({ quality: 0.6 });
```

`quality: 0.6` ajusta la compresión JPEG **pero no la resolución**. Una cámara de 12 MP da
4000×3000 y al 60% sigue pesando 1,5-3 MB. Y cada foto se sube **dos veces**: una a
`/tickets/analyze` para el OCR y otra a `/tickets` al confirmar. **Son 3-6 MB por carga.**

No hay `expo-image-manipulator` en `package.json`.

**Consecuencia.** Sobre la conexión de un yacimiento eso es lento y caro, y encima ocurre en
el peor momento: el operario esperando con el ticket en la mano. Es exactamente el problema
que motivó paginar el historial (`services/tickets.ts`) — el mismo criterio no se aplicó a lo
que más pesa.

**Arreglo.** `expo-image-manipulator` para redimensionar a ~1600px de lado mayor antes de
subir. Un ticket de surtidor es legible de sobra a esa resolución y el archivo cae a
~200-400 KB: **cinco a diez veces menos**. Además, reutilizar el archivo ya subido en
`/analyze` al confirmar, en vez de mandarlo de nuevo, ahorra la mitad de lo que queda.

---

## [ ] UI-09 — La hoja de detalle entera es un botón con `onPress` vacío

**Dónde:** `components/fuel/LoadDetailModal.tsx:73`.

```tsx
<Pressable style={styles.sheet} onPress={() => {}}>
```

Es el truco para que tocar la tarjeta no atraviese al overlay y cierre el modal. Funciona,
pero convierte **toda la hoja de detalle en un control táctil**: un lector de pantalla la
anuncia como un botón gigante y sin nombre que envuelve todo el contenido, y en Android
dispara el ripple al tocar cualquier parte. Suma a `A11Y-00`.

**Arreglo.** Un `View` con `onStartShouldSetResponder={() => true}`, que frena la propagación
sin declarar un control. O mejor: sacar el `Pressable` del overlay y poner el área de cierre
como hermano detrás de la hoja, en vez de como padre.

**Del mismo archivo:** `DetailSheet` devuelve un Fragment `<>...</>` con **un solo hijo**
(líneas 70-127). Sobra, y dejó la indentación del bloque corrida dos espacios.

---

## [x] BLD-01 — `AGENTS.md` manda leer los docs de Expo 56; está instalado el 54

> **Resuelto**: `mobile/AGENTS.md` apunta a `https://docs.expo.dev/versions/v54.0.0/`.

**Dónde:** `mobile/AGENTS.md` contra `mobile/package.json`.

```
AGENTS.md:  https://docs.expo.dev/versions/v56.0.0/
package.json: "expo": "~54.0.35"
```

El archivo existe justamente para que nadie escriba código contra APIs de memoria — y apunta
dos majors adelante de lo instalado. Cualquiera que lo siga al pie de la letra va a usar APIs
que en este proyecto no existen: el error exacto que el archivo quiere prevenir.

**Arreglo.** Apuntar a `v54.0.0`, y agregar la nota de mantener la versión sincronizada con
`package.json` cuando se actualice el SDK.

**Del mismo archivo:** `app.json` declara `"userInterfaceStyle": "light"` mientras la app es
íntegramente oscura (`colors.bg = #16181B`, `<StatusBar style="light" />`). Hoy no rompe nada
porque no hay estilos que reaccionen al esquema del sistema, pero es una declaración que
contradice al producto y va a confundir al primero que agregue soporte de tema.

---

## [ ] STATE-02 — Contextos sin memoizar y `setForm({ ...form })` en cada tecla

**Dónde:** `context/AuthContext.tsx:110`, `context/TutorialContext.tsx:35`, y los formularios
de `app/(administrador)/index.tsx`.

**Contextos.** Los dos providers pasan un objeto literal nuevo en cada render:

```tsx
<AuthContext.Provider value={{ user, isLoading, login, logout }}>
<TutorialContext.Provider value={{ open: () => { setStep(0); setVisible(true); } }}>
```

El valor cambia de identidad siempre, así que **todo consumidor de `useAuth()` o
`useTutorial()` re-renderiza en cada render del provider**. `AuthProvider` envuelve la app
entera. Hoy se disimula porque su estado casi no cambia, pero es una bomba de relojería:
el día que alguien agregue estado que cambie seguido al `AuthProvider`, re-renderiza todo.

`logout` sí está en `useCallback`; `login` (línea 103) no.

**Formularios.** El patrón es `setForm({ ...form, campo: t })` en cada `onChangeText` —
aparece **más de veinte veces** entre `VehiclesABM` y `EmpleadosList`. Lee `form` del closure
del render en curso en vez de usar la forma funcional.

**Arreglo.** `useMemo` sobre los valores de contexto (y `useCallback` en `login`), y la forma
funcional en los formularios: `setForm((f) => ({ ...f, campo: t }))`. Es mecánico y elimina
toda una clase de bugs de closure viejo.

---

## [ ] UI-10 — El desplegable de filtros no se acota al viewport

**Dónde:** `components/fuel/FilterDropdown.tsx:88-100`.

El panel se posiciona en absoluto con lo que devuelve `measureInWindow`:

```tsx
top: triggerLayout.y + triggerLayout.height + 4,
left: triggerLayout.x,
width: triggerLayout.width,
```

Sin acotar contra el alto de la pantalla. La lista puede llegar a 260 px más el botón
"Listo", así que **si el trigger queda en la mitad inferior, el panel se dibuja fuera de la
pantalla** y las últimas opciones no se pueden tocar. No hay lógica de "abrir hacia arriba"
si no entra abajo.

Con pocos vehículos y el panel de filtros arriba de todo no se nota. Con la lista de personas
del filtro de Analítica, y en pantallas chicas, sí.

**Arreglo.** Comparar contra `Dimensions.get('window').height` y, si no entra abajo, abrir
hacia arriba (`bottom: alto - triggerLayout.y + 4`). Y acotar `maxHeight` al espacio real
disponible en vez de un 260 fijo.

---

## [ ] PERF-04 — `BarChart` divide por cero cuando todos los valores son cero

**Dónde:** `components/fuel/BarChart.tsx:8` y `:18`.

```tsx
const max = Math.max(...data.map((d) => d.v));
// ...
width: `${(d.v / max) * 100}%`,
```

Si todas las barras valen `0` —un período con tickets registrados pero de gasto cero, o
importes que redondean a cero— entonces `max === 0` y `0 / 0` es `NaN`. El `width` queda en
`"NaN%"`: React Native tira un warning de estilo y la barra no se dibuja.

El caso de `data` vacío sí está cubierto (el `.map` no corre), pero el chequeo que falta es
el de `max`.

**Arreglo.** `const max = Math.max(...data.map((d) => d.v)) || 1;`, o saltear el render del
track cuando `max === 0`.

**Menor, del mismo archivo:** las barras usan `key={i}`. Como la lista se reordena por ranking
al cambiar de período, conviene una clave estable (`d.l`) para que React no reutilice nodos
entre datos distintos.

---

## [ ] NAV-01 — Doble guardia de navegación entre el layout raíz e `index`

**Dónde:** `app/_layout.tsx:39-64` y `app/index.tsx:14-26`.

Las dos rutas deciden a dónde mandar al usuario según sesión y rol: `index.tsx` con un
`<Redirect>` y `_layout.tsx` con un `router.replace()` dentro de un `useEffect`. Sobre la ruta
`/`, **las dos se disparan**: `segments[0]` es `undefined`, así que el guard del layout no
detecta ningún grupo y también redirige.

El comentario de `index.tsx` explica la intención —que el primer frame ya apunte al destino
correcto, sin parpadeo— y es un motivo válido. Pero el resultado son dos navegaciones para un
mismo destino, y dos lugares donde mantener la misma regla de roles. Si mañana entra un tercer
rol, hay que acordarse de los dos.

**Arreglo.** Dejar una sola fuente de verdad. Lo más limpio: una función
`rutaSegunSesion(user)` en un módulo aparte, que consuman los dos puntos, para que la regla
de roles esté escrita una sola vez aunque se evalúe en dos.

---

## [ ] UI-11 — No hay pull-to-refresh en ninguna lista

**Dónde:** `app/(empleado)/historial.tsx`, `app/(empleado)/index.tsx`, y las listas del panel.

Ninguna lista tiene `RefreshControl`. Refrescar depende de `useFocusEffect`: hay que salir de
la pestaña y volver.

En una app de campo con conexión intermitente, el gesto de tirar para actualizar es el
reflejo que tiene todo el mundo cuando algo no cargó. Acá no hace nada, y la alternativa
—cambiar de pestaña y volver— no es evidente.

**Arreglo.** `RefreshControl` en el `FlatList` del historial y en el scroll de la flota,
cableado al `refetch` que `useFetch` ya devuelve. Es de pocas líneas, porque el refetch ya
existe.

---

## Resumen para quien tenga cinco minutos

Si de todo este documento solo se atienden tres cosas, que sean éstas:

1. **`DATA-00`** — el historial del operario muestra `$0` en producción, hoy. Un campo de
   diferencia (`t.precioUnitario` en vez del lookup al catálogo).
2. **`STATE-00`** — dos `setState` que faltan en un reset hacen que la lectura del odómetro
   de un vehículo se registre en otro, y eso corrompe el cálculo de consumo, que es el
   número por el que existe la aplicación.
3. **`BLD-00`** — sin ESLint y sin un solo test, los dos arreglos de arriba se pueden volver
   a romper mañana y nadie se va a enterar hasta que un operario avise.
