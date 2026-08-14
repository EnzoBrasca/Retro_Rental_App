# Auditoría de seguridad — RetroRental

Fecha de la auditoría: 2026-08-13
Alcance: `backend/` (Spring Boot), `mobile/` (Expo/React Native), `infra/` (Docker Compose, nginx).
Contexto: la app se va a instalar en los dispositivos de los empleados del cliente. El
modelo de amenaza incluye tanto a un atacante externo con la URL de la API como a un
empleado con credenciales válidas y acceso físico a su teléfono.

## Cómo usar este documento

Cada hallazgo tiene una casilla. Al implementar la mitigación, marcá la casilla y agregá
el commit o PR donde se resolvió. Lo que sigue sin marcar es lo que falta.

## Estado general

| Severidad | Total | Resueltas | Pendientes |
| --- | --- | --- | --- |
| Crítica | 2 | 2 | 0 |
| Alta | 3 | 3 | 0 |
| Media | 4 | 2 | 2 |
| Baja | 3 | 1 | 2 |

## Lo que ya está bien (no tocar)

Se documenta para que un refactor futuro no lo revierta sin querer:

- El rol NO se acepta desde el body en `/auth/register` ni en `/admin/empleados`:
  siempre se fuerza `Rol.EMPLEADO` en el servicio.
- Passwords con BCrypt (`SecurityConfig.passwordEncoder`).
- Login rechaza empleados con `fechaBaja`, con mensaje genérico que no revela si la
  cuenta existe o solo está inactiva.
- En producción ningún servicio de datos publica puertos: el único camino de entrada
  es nginx.
- Flyway con `ddl-auto: validate` en todos los perfiles; el schema nunca se toca solo.
- `open-in-view: false`.
- `forward-headers-strategy: framework` para que el rate limit cuente por IP real.
- Secretos fuera del repo (`.env*` ignorados, solo se versionan los `.example`).
- `GlobalExceptionHandler` devuelve mensajes genéricos en el 500 y no filtra internals.

---

# CRÍTICAS

## [x] SEC-01 — `/auth/register` es público: cualquiera en internet crea una cuenta de empleado

**Ubicación:** `backend/src/main/java/com/retrorental/backend/config/SecurityConfig.java`
(lista de `permitAll`), `AuthController.register`, `AuthService.register`.

**Descripción:** el registro público solo exige nombre, apellido, un documento de 7 a 9
dígitos que no esté tomado, una password y un teléfono. Ningún dato se verifica contra la
empresa. Quien conozca la URL de la API obtiene una cuenta `EMPLEADO` válida.

**Impacto:** con esa cuenta un tercero puede:
- Leer el catálogo completo: patentes de la flota (`GET /vehiculos`), proveedores,
  precios y herramientas.
- Tomar vehículos (`POST /me/vehiculos/{id}`) y acaparar la flota, dejando a los
  empleados reales sin poder registrar cargas.
- Crear tickets falsos, que envenenan las estadísticas del panel del Jefe y además
  actualizan el odómetro y el consumo de vehículos reales.
- Consumir el OCR pago (ver SEC-02).

El rate limit actual (10 intentos por minuto y por IP) permite del orden de 14.000 altas
diarias desde una sola dirección.

**Mitigación aplicada:** padrón de documentos habilitados. Se descartó cerrar el registro
porque el self-service es una funcionalidad que el cliente quiere conservar; el padrón la
mantiene intacta y sin agregar un solo paso al empleado.

El registro sigue siendo público y el formulario de la app **no cambió**. Lo que se agregó
es que el backend, antes de crear la cuenta, verifica que el documento esté en un padrón
que carga el administrador, y que el apellido coincida con el registrado ahí.

Decisiones de diseño que no hay que revertir:

- **El padrón es una tabla aparte (`empleados_habilitados`), no filas incompletas en
  `personas`.** La alternativa exigía quitar el `NOT NULL` de nombre, apellido, username,
  password, rol y teléfono — toda la tabla de identidad — y convertía `/auth/register` de
  "documento ya existe → rechazar SIEMPRE" en un `UPDATE` condicional sobre una fila
  existente, en el único endpoint sin autenticar de la API.
- **El padrón se valida ANTES que el documento duplicado.** Al revés, el 409 del duplicado
  delataría qué documentos tienen cuenta para cualquier número probado.
- **Los tres rechazos (fuera del padrón, apellido que no coincide, habilitación ya usada)
  devuelven el mismo código y el mismo mensaje.** Distinguirlos convierte el endpoint en un
  oráculo para enumerar al personal del cliente. Hay un test que lo verifica.
- **El apellido se compara normalizado** (sin acentos, sin distinguir mayúsculas, sin
  espacios de más). Ser estricto no agrega seguridad —quien conoce el documento conoce el
  apellido— y solo traba al empleado real.
- **Una habilitación ya consumida no se puede borrar.** Borrarla no le quita el acceso a
  quien ya se registró y destruye la trazabilidad. Para eso está la baja de empleado.

Archivos:

- `backend/.../db/migration/V9__agregar_padron_empleados_habilitados.sql`
- `backend/.../model/EmpleadoHabilitado.java`, `repository/EmpleadoHabilitadoRepository.java`
- `backend/.../service/HabilitadoService.java`
- `backend/.../controller/AdminHabilitadoController.java` (`/admin/habilitados`)
- `backend/.../service/AuthService.java` (validación en `register`)
- `backend/.../service/EmpleadoService.java` (el alta manual consume la habilitación)
- `mobile/services/habilitados.ts`, `mobile/app/(administrador)/index.tsx` (`HabilitadosList`)

Cobertura: `HabilitadoServiceTest` (16 casos) y `AuthServiceTest` (9 casos). Antes de este
cambio `AuthService` no tenía ningún test: `AuthControllerTest` lo mockea.

**Resuelto en:** _(pendiente de commit)_

## [x] SEC-02 — `POST /tickets/analyze` sin límite: costo de OCR abierto y agotamiento del pool de hilos

**Ubicación:** `TicketController.analyze`, `TicketService.analyze`,
`MistralTicketAnalysisService`. El rate limit existente (`LoginRateLimitFilter`) cubre
únicamente `/auth/login` y `/auth/register`.

**Descripción:** cada llamada dispara una petición de OCR paga a Mistral, con hasta 10 MB
de imagen y un timeout de 30 segundos. Ninguna ruta autenticada tiene límite de uso.

**Impacto:** doble. Por un lado consumo económico directo sobre la cuenta de Mistral. Por
otro, cada petición mantiene un hilo de Tomcat bloqueado hasta 30 segundos; con el pool
por defecto (200 hilos), un cliente en bucle deja la API entera sin capacidad de
respuesta para el resto de los empleados. No hace falta intención maliciosa: un reintento
mal implementado en la app produce el mismo efecto.

**Mitigación aplicada:** dos controles independientes, porque el endpoint tiene dos
riesgos distintos y uno solo no cubre al otro.

**1. Límite de consumo por USUARIO** (`AnalyzeRateLimitFilter`): 20 análisis por hora,
ventana fija, configurable por `app.rate-limit.analyze.*`. Ataja el costo económico.

Por qué por usuario y no por IP como el de login: los empleados cargan desde el campo con
datos móviles y varios comparten la IP de salida de la operadora, así que un límite por IP
castigaría a compañeros que no hicieron nada. Como el endpoint exige autenticación,
siempre hay cuenta a la que atribuir el consumo — a diferencia de `/auth/login`, donde
todavía no la hay y la IP es lo único que existe. Hay un test que cubre exactamente esto.

**2. Mamparo de concurrencia** (`Semaphore` justo en `MistralTicketAnalysisService`): 5
análisis simultáneos como máximo, con espera de 5s antes de rendirse con 503
(`ANALYSIS_BUSY`). Ataja el agotamiento del pool de hilos.

El límite por usuario NO alcanza para esto: veinte empleados distintos, cada uno dentro de
su cuota, igual dejarían veinte hilos de Tomcat bloqueados hasta 30s con veinte imágenes en
base64 vivas en memoria. El mamparo convierte una saturación del OCR en un 503 acotado a
ese endpoint, en vez de una caída de toda la API.

Decisiones de diseño que no hay que revertir:

- **El permiso del semáforo se toma ANTES de `toDataUri`**, no solo antes del HTTP:
  codificar la imagen en base64 cuesta ~13 MB por cada 10 MB de foto. Tomarlo después
  dejaría el pico de memoria proporcional a la cantidad de hilos, que es justo lo que el
  mamparo existe para acotar.
- **El `release()` va en `finally`.** Un permiso que no se devuelve reduce el cupo para
  siempre; a los pocos errores el endpoint queda muerto sin que nada lo explique.
- **`ANALYSIS_BUSY` es un código distinto de `ANALYSIS_UNAVAILABLE`.** El segundo significa
  "no configurado" y reintentar no sirve; el primero significa "saturado" y reintentar sí
  sirve. El mobile puede decir cosas distintas.
- **El semáforo es justo (`fair`)**: sin eso, con carga sostenida un request puede quedar
  postergado indefinidamente mientras otros que llegaron después se cuelan.
- **El filtro va DESPUÉS de `JwtFilter`** en la cadena (`addFilterAfter`), que es quien deja
  la autenticación en el `SecurityContext`. Invertirlo lo dejaría sin nadie a quien contarle
  la llamada y el límite no aplicaría nunca.
- **La lógica de ventana se extrajo a `FixedWindowCounter`** y ahora la comparten los dos
  frenos. Estaba duplicada: mantener dos copias de un control de seguridad significa que el
  día que se arregla un borde en una, la otra queda atrás sin que nadie lo note.

Archivos:

- `backend/.../security/FixedWindowCounter.java` (nuevo, compartido)
- `backend/.../security/LoginRateLimitFilter.java` (refactor: usa el contador compartido)
- `backend/.../security/AnalyzeRateLimitFilter.java` (nuevo)
- `backend/.../service/MistralTicketAnalysisService.java` (mamparo)
- `backend/.../config/MistralProperties.java`, `exception/ErrorCode.java`,
  `config/SecurityConfig.java`, `resources/application.yml`

Cobertura: `AnalyzeRateLimitFilterTest` (5 casos). `LoginRateLimitFilterTest` (6 casos)
sigue en verde sin tocarse, que es lo que prueba que el refactor del contador no cambió el
comportamiento del freno de login.

**Nota operativa:** el filtro corta antes de que Spring resuelva el multipart, así que un
pedido rechazado no llega a parsear la imagen — pero el cuerpo ya se subió. Para frenar el
ancho de banda hace falta el límite en nginx (SEC-07).

**Resuelto en:** _(pendiente de commit)_

---

# ALTAS

## [x] SEC-03 — IDOR en `GET /tickets/{id}`: cualquier empleado lee los tickets de todos

**Ubicación:** `TicketService.get` (`TicketService.java:611`), expuesto por
`TicketController.get`.

**Descripción:** el método busca el ticket por id y lo devuelve sin comparar contra el
usuario autenticado. `listMine` sí filtra por empleado; `get` no.

**Impacto:** un empleado enumera ids consecutivos y obtiene los tickets de toda la
empresa: montos, proveedores, vehículos y URLs presignadas frescas de las fotos.

**Mitigación aplicada:** `TicketService.get(id)` pasó a `get(id, username)`. Resuelve la
persona autenticada y devuelve el ticket solo si es su dueña o tiene rol `ADMINISTRADOR`.
El controller pasa `authentication.getName()`; el id del solicitante nunca viene del
request.

Decisiones de diseño que no hay que revertir:

- **El rechazo es 404, no 403.** Un 403 confirma que ese ticket existe, y con ids
  correlativos eso alcanza para contar las cargas de la empresa aunque no se pueda leer
  ninguna. Hay un test que verifica que el rechazo por ajeno y el rechazo por inexistente
  sean indistinguibles en código y mensaje.
- **La propiedad se compara por ID de persona, no por username.** El username es único hoy,
  pero el ID es la identidad real de la fila: si algún día el username fuera editable,
  comparar por texto convertiría un cambio de nombre en un agujero de permisos.

Alcance: el mobile no consume este endpoint (usa `/tickets/me`, `/tickets/analyze` y
`/admin/tickets/{id}`), así que el cambio es puramente de servidor y no requiere tocar la
app ni publicar una versión nueva.

Archivos: `service/TicketService.java`, `controller/TicketController.java`.

Cobertura: 5 casos nuevos en `TicketServiceTest` (dueño, empleado ajeno, indistinguibilidad
404, administrador, inexistente) y `TicketControllerTest` verifica que el controller pase
el usuario del JWT al service.

**Resuelto en:** _(pendiente de commit)_

## [x] SEC-04 — Subida de archivos sin validar: XSS almacenado en el dominio de archivos

**Ubicación:** `MinioStorageService.upload` (`MinioStorageService.java:58` y `:65`),
`buildObjectKey`; server block de `files.${APP_DOMAIN}` en
`infra/nginx/templates/app.conf.template`.

**Descripción:** el servicio acepta cualquier archivo. No valida el content-type, no
valida los magic bytes, persiste el `Content-Type` declarado por el cliente y deriva la
extensión de `getOriginalFilename()`, también controlado por el cliente.

**Impacto:** nginx proxea `files.${APP_DOMAIN}` directamente a MinIO, que sirve el objeto
con el content-type almacenado. Un archivo HTML declarado como `text/html` queda servido
desde el dominio propio, produciendo XSS almacenado en el mismo host donde viven las URLs
presignadas.

**Mitigación aplicada:** `ImageValidator` decide por los **magic bytes** del contenido
(JPEG `FF D8 FF`, PNG `89 50 4E 47 0D 0A 1A 0A`, WebP `RIFF`…`WEBP`), nunca por el header.

Evidencia de que el header no vale nada: la propia app mobile lo manda **hardcodeado**
(`name: 'ticket.jpg'`, `type: 'image/jpeg'` en `services/tickets.ts:127-131`), sin mirar el
archivo. Es una etiqueta que elige el emisor.

Decisiones de diseño que no hay que revertir:

- **El validador se llama en los TRES caminos, no en dos.** `ticketFoto` y `tableroFoto`
  pasan por `MinioStorageService.upload`, pero `POST /tickets/analyze` **no toca el
  storage**: manda los bytes directo a Mistral con nuestra API key. Validar sólo dentro del
  storage habría dejado ese camino abierto. En `analyze` se valida *antes* de llamar al OCR
  para no gastar un cupo del mamparo (SEC-02) en un archivo ya rechazable.
- **Extensión y content-type salen del formato detectado**, no de `getOriginalFilename()` ni
  de `getContentType()`. Como efecto secundario, `buildObjectKey` dejó de tocar el nombre
  del cliente, lo que cierra un vector de path traversal (`../../algo`) que estaba presente.
- **`RIFF` solo no es WebP**: `.wav` y `.avi` también empiezan así. Se verifican además los
  bytes 8-11. Hay un test.
- **SVG queda fuera de la whitelist a propósito**: es texto y puede llevar `<script>`.
- **Los `add_header` de nginx llevan `always`**: sin eso no se aplican a respuestas 4xx/5xx,
  que es donde un cuerpo controlado por el atacante podría colarse.

HEIC no es un riesgo: `expo-camera` devuelve JPEG y `expo-image-picker` con `quality: 0.6`
reencodea, así que el formato por defecto de iPhone no llega al backend.

Archivos: `service/ImageValidator.java` (nuevo), `service/MinioStorageService.java`,
`service/TicketService.java`, `exception/ErrorCode.java`,
`infra/nginx/templates/app.conf.template`.

Cobertura: `ImageValidatorTest` (13 casos, centrados en el archivo que miente) y 2 casos
nuevos en `TicketServiceTest` para el camino de `analyze`. **Ese camino no tenía ningún test
y `ImageValidator` se inyectaba en `null`**: la suite daba verde sin ejecutarlo.

Verificación de nginx: `nginx -t` sobre la config renderizada con `envsubst` → *syntax is ok
/ test is successful*. **Requiere recargar el contenedor de nginx al desplegar**; los tests
de Java no cubren esa parte.

**Resuelto en:** _(pendiente de commit)_

## [x] SEC-05 — El token JWT se guarda en AsyncStorage, sin cifrar

**Ubicación:** `mobile/context/AuthContext.tsx` (clave `user`), `mobile/services/api.ts`
(`getToken`), `mobile/app.json` (bloque `android`).

**Descripción:** AsyncStorage en Android es un archivo SQLite en claro dentro del sandbox
de la app. No hay respaldo del Keystore. Además `app.json` no desactiva `allowBackup`, de
modo que el almacenamiento de la app puede extraerse por backup de Android.

**Impacto:** en un dispositivo rooteado, o con backup habilitado, el token de sesión se
extrae y se reutiliza contra la API. Son teléfonos de empleados en el campo, no equipos
administrados por la empresa.

**Mitigación aplicada:** el token pasó a `expo-secure-store` (`~15.0.8`) y el
almacenamiento se centralizó en `services/sessionStorage.ts`.

Decisiones de diseño que no hay que revertir:

- **El token se guarda SEPARADO del perfil**, no como un objeto único. SecureStore tiene un
  límite práctico de ~2048 bytes por valor en iOS: si algún día el `AuthResponse` crece, la
  escritura fallaría en el teléfono del empleado y justo al guardar la sesión. El token solo
  nunca se acerca a ese límite. Nombre, apellido, username, rol, teléfono y `expiresAt` no
  son secretos y siguen en AsyncStorage.
- **La sesión vieja se BORRA, no se migra** (`purgeLegacySession`, clave `user`). Mover un
  secreto de un almacén inseguro a uno seguro no lo vuelve seguro: ya estuvo expuesto. El
  usuario inicia sesión una vez más y el token nace protegido.
- **`readSession` devuelve null si falta cualquiera de las dos partes** y limpia el resto.
  Un perfil sin token haría morir cada request con 401; un token sin perfil no permite ni
  decidir a qué pantalla ir según el rol.
- **Guard explícito para web** (`Platform.OS !== 'web'`): SecureStore no existe en web y
  `app.json` declara un bundler web. Sin el guard, abrir la app en un navegador reventaría
  al arrancar. En web cae a AsyncStorage, que NO da la garantía de hardware — aceptable
  porque web no es destino de producción; si algún día lo fuera, hay que rediscutirlo.
- **`allowBackup: false`** en el bloque android. Sin eso el valor por defecto es `true` y el
  sistema puede copiar el almacenamiento de la app fuera del teléfono (nube o `adb backup`).
- **`lastUsername` sigue en AsyncStorage y bajo otra clave**: no es secreto y debe sobrevivir
  al logout para prellenar el login.

Archivos: `services/sessionStorage.ts` (nuevo), `services/api.ts`, `context/AuthContext.tsx`,
`app.json`, `package.json`. `services/session.ts` quedó sin cambios.

Verificación: `npx tsc --noEmit` limpio; no queda ningún acceso al token vía AsyncStorage
fuera del fallback de web.

**Pendiente de despliegue:** requiere **build nativo con EAS**, no un update OTA
(`expo-secure-store` trae código nativo; el config plugin ya quedó registrado en
`app.json`). Hay que subir `versionCode` antes de buildear — deliberadamente NO se tocó,
es una decisión de release. Al instalar la versión nueva, las sesiones existentes se
cortan y hay que volver a iniciar sesión una vez.

**Resuelto en:** _(pendiente de commit)_

---

# MEDIAS

## [x] SEC-06 — JWT sin revocación; el filtro no verifica que el usuario siga activo

**Ubicación:** `security/JwtFilter.java`.

**Descripción:** el filtro valida la firma y construye la autenticación con el `subject` y
el claim `rol` del token, sin consultar la base. El campo `personaRepository` está
inyectado en la clase y nunca se usa.

**Impacto:** un empleado dado de baja conserva el acceso hasta que expire su token
(actualmente 1 hora). No existe ningún mecanismo para revocar una sesión de inmediato. Un
cambio de rol tampoco surte efecto hasta la próxima emisión. El riesgo escala si en el
futuro se aumenta `JWT_EXPIRATION` para reducir la fricción de re-login.

**Mitigación aplicada:** `JwtFilter` carga la persona por username en cada request
autenticado. Si no existe o es un `Empleado` con `fechaBaja`, el token se ignora y la cadena
de seguridad responde 401. El **rol sale de la base, no del claim**.

Decisiones de diseño que no hay que revertir:

- **Sin caché.** Es una consulta por request autenticado. A la escala de esta app (una
  empresa, un puñado de empleados) es irrelevante, y se prefiere pagarla antes que sostener
  un modelo donde revocar el acceso es imposible. Si el volumen algún día lo justifica, el
  lugar del caché corto es ese filtro.
- **El rol de la base y no del claim**: así degradar a alguien de administrador a empleado
  aplica en el request siguiente, y no recién cuando venza su token.
- **Mismo criterio de "activo" que `AuthService.login`**: si el login rechaza a alguien, su
  token viejo tampoco puede seguir sirviendo. Que las dos puertas usen la misma regla es lo
  que evita que una quede abierta.
- **No se distingue "no existe" de "dado de baja"**: para el portador del token el resultado
  es el mismo y el motivo no es asunto suyo.

Archivos: `security/JwtFilter.java`.

Cobertura: `JwtFilterTest` (8 casos). **Este filtro no tenía ningún test**: los tests de
controller usan `@WithMockUser`, que lo saltea por completo, así que la suite daba verde sin
ejecutarlo nunca.

**Resuelto en:** _(pendiente de commit)_

## [ ] SEC-07 — El rate limit no existe a nivel de proxy

**Ubicación:** `infra/nginx/templates/app.conf.template`;
`security/LoginRateLimitFilter.java`.

**Descripción:** el único límite es en memoria dentro de la aplicación, se reinicia con el
contenedor (limitación ya documentada en el propio filtro) y sólo cubre dos rutas. nginx
no define `limit_req` ni `limit_conn`.

**Impacto:** una inundación de peticiones llega íntegra a Spring y consume hilos y
conexiones a base antes de que nada la frene.

**Mitigación propuesta:** definir `limit_req_zone` y `limit_conn_zone` en nginx, con una
zona más estricta para `/auth/`, de modo que el corte ocurra antes de que la petición
cueste recursos de aplicación.

**Resuelto en:** _(pendiente)_

## [ ] SEC-08 — nginx sin cabeceras de seguridad ni `default_server`

**Ubicación:** `infra/nginx/templates/app.conf.template`.

**Descripción:** faltan `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`,
`X-Frame-Options` y `server_tokens off`. Tampoco hay un `default_server` que rechace
peticiones con un `Host` desconocido.

**Impacto:** se pierde la protección de HSTS ante un downgrade a HTTP, se habilita el
sniffing de contenido, se filtra la versión de nginx, y cualquier dominio apuntado a la IP
del servidor entra por el primer server block.

**Mitigación propuesta:** agregar las cabeceras en ambos server blocks TLS, `server_tokens
off`, y un server block `default_server` que responda 444.

**Resuelto en:** _(pendiente)_

## [x] SEC-09 — La API completa de MinIO queda publicada en `files.${APP_DOMAIN}`

**Ubicación:** `infra/nginx/templates/app.conf.template`, server block de
`files.${APP_DOMAIN}`.

**Descripción:** el `location /` proxea todo `minio:9000`, no sólo las descargas de
objetos por URL presignada.

**Impacto:** toda la superficie de la API S3 de MinIO queda expuesta a internet. El bucket
privado contiene el riesgo hoy, pero cualquier error de configuración de política o
cualquier vulnerabilidad futura de MinIO pasa a ser explotable de forma remota.

**Mitigación aplicada:** el `location /` que proxeaba todo `minio:9000` se partió en dos:

- `location /` → `return 404`. Todo lo que no sea el bucket de tickets deja de existir hacia
  afuera. 404 y no 403 porque un 403 confirmaría que hay algo detrás.
- `location /${APP_MINIO_BUCKET}/` → `limit_except GET HEAD { deny all; }` + proxy. Las URLs
  presignadas que emite el backend son GET; un PUT o un DELETE contra este host no tiene
  ningún caso de uso legítimo.

El nombre del bucket viaja como `APP_MINIO_BUCKET` (definido en el servicio nginx de
`docker-compose.prod.yml`) porque `NGINX_ENVSUBST_FILTER="^APP_"` solo sustituye variables
con ese prefijo. `Host` se sigue reenviando sin reescribir: la firma presignada se calcula
sobre él.

**Verificación end-to-end** (MinIO + nginx reales en Docker, no solo `nginx -t`):

| Petición | Resultado | Quién responde |
| --- | --- | --- |
| `GET /tickets/tickets/<obj>` | llega a MinIO | MinIO (XML) |
| `PUT` / `DELETE` sobre el bucket | 403 | **nginx** (HTML) |
| `GET /` (listado de buckets) | 404 | **nginx** |
| `POST /minio/admin/v3/info` | 404 | **nginx** |
| `GET /otro-bucket/x` | 404 | **nginx** |

El cuerpo de la respuesta es lo que desambigua quién bloquea: HTML = nginx, XML = MinIO.

También se verificó empíricamente, generando URLs presignadas reales, que las rutas tienen
la forma `/tickets/tickets/<uuid>.jpg` y `/tickets/tableros/<uuid>.jpg` — ambas bajo el
prefijo del bucket, así que el `location` las cubre. **Limitación honesta del test:** no se
logró producir una firma válida dentro del harness (mc firma contra un alias `https`
mientras el MinIO de prueba habla `http`), así que el GET terminó en 403 de MinIO. Se
descartó que fuera culpa de nginx comprobando que la misma URL da el mismo 403 **sin nginx
en el camino**. Lo que nginx debe garantizar —que el GET se enrute y el resto se corte—
quedó verificado.

Archivos: `infra/nginx/templates/app.conf.template`, `docker-compose.prod.yml`.

**Requiere recargar nginx al desplegar.**

**Resuelto en:** _(pendiente de commit)_

---

# BAJAS

## [x] SEC-10 — Enumeración de documentos en el registro

**Ubicación:** `AuthService.register`, código de error `DOCUMENTO_ALREADY_EXISTS`.

**Descripción:** la respuesta distingue explícitamente el caso de documento ya registrado,
permitiendo verificar si un DNI dado tiene cuenta.

**Impacto:** filtración menor de datos personales.

**Actualización tras SEC-01:** el registro sigue siendo público (decisión de producto), así
que este hallazgo NO se resolvió solo como se había anticipado. Sí quedó muy acotado: el
padrón se valida primero y devuelve un mensaje genérico, de modo que un documento
cualquiera ya no revela nada. El 409 solo aparece para documentos que están en el padrón
del cliente, es decir para gente que el atacante ya tendría que conocer.

**Mitigación aplicada:** el registro público pasó a tener **una sola respuesta de fracaso**.
El `409 DOCUMENTO_ALREADY_EXISTS` se reemplazó por el mismo `403 REGISTRO_NO_HABILITADO` con
el mismo mensaje que usa el padrón, así el endpoint no sirve para averiguar nada: ni qué
documentos están en el padrón, ni cuáles ya tienen cuenta.

La regla de negocio NO cambió: documento que ya existe se sigue rechazando siempre, antes de
cualquier escritura. Lo único que cambió es qué se le cuenta a quien pregunta.

De paso se corrigió un problema de UX que había introducido SEC-01: quien ya tenía cuenta y
volvía a registrarse recibía *"pedile a tu jefe que te habilite"*, cuando su jefe ya lo había
habilitado. El mensaje nuevo cubre las dos salidas reales sin distinguir cuál aplica:

> No podés registrarte con esos datos. Si ya tenés cuenta, iniciá sesión. Si no, pedile a tu
> jefe que te habilite en el sistema.

El `409` explícito **sigue existiendo en `POST /admin/empleados`**, y ahí corresponde: quien
pregunta es un administrador autenticado que necesita saber por qué falló el alta.

Archivos: `service/AuthService.java`, `service/HabilitadoService.java` (el mensaje pasó a
constante pública compartida).

Cobertura: test nuevo en `AuthServiceTest` que compara el rechazo del padrón contra el
rechazo por duplicado y falla si alguien vuelve a diferenciarlos.

**Resuelto en:** _(pendiente de commit)_

## [ ] SEC-11 — Sin límites de recursos en los contenedores de producción

**Ubicación:** `docker-compose.prod.yml`, `docker-compose.yml`.

**Descripción:** ningún servicio declara `mem_limit` ni `cpus`. El backend tampoco define
un healthcheck propio (postgres y minio sí lo tienen).

**Impacto:** un consumo excesivo de memoria en el backend puede provocar un OOM que afecte
al host completo, incluida la base de datos. Sin healthcheck, Compose no puede detectar
que el backend quedó vivo pero sin responder.

**Mitigación propuesta:** declarar límites de memoria y CPU por servicio, y un healthcheck
sobre `/health` para el backend.

**Resuelto en:** _(pendiente)_

## [ ] SEC-12 — Sin registro de eventos de seguridad

**Ubicación:** `AuthService.login`, `SecurityConfig` (entry point y access denied handler),
`LoginRateLimitFilter`.

**Descripción:** no se registran logins fallidos, respuestas 403, ni activaciones del rate
limit. En el perfil `prod` el nivel de log raíz es `WARN`.

**Impacto:** ante un incidente no hay traza para reconstruir qué ocurrió, desde qué origen
ni contra qué cuentas.

**Mitigación propuesta:** loguear a nivel WARN los intentos de login fallidos (con
username y IP), los 403 y los cortes por rate limit.

**Resuelto en:** _(pendiente)_
