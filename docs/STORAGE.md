# Almacenamiento de imágenes (backend)

Esta guía explica cómo está armado el almacenamiento de imágenes de tickets en
el backend: la integración con MinIO, el flujo de subida vía `multipart/form-data`,
y por qué las URLs nunca se persisten en la base de datos.

---

## 1. Visión general

El almacenamiento se apoya en cinco piezas, cada una con UNA responsabilidad:

| Pieza | Archivo | Responsabilidad |
|-------|---------|-----------------|
| Config de propiedades | `config/MinioProperties.java` | Mapear `minio.*` de `application.yml` de forma type-safe. |
| Config de clientes | `config/MinioConfig.java` | Definir los dos `MinioClient` (interno y público — ver sección 3). |
| Contrato de storage | `service/StorageService.java` | Interfaz: `upload` / `getUrl` / `delete`. No sabe que existe MinIO. |
| Implementación | `service/MinioStorageService.java` | Adaptador concreto contra MinIO. |
| Excepción de dominio | `exception/StorageException.java` | Envuelve errores de MinIO en una excepción propia del backend. |

Y dos consumidores de `StorageService`:
- `controller/FileController.java` — endpoint genérico de archivos (`POST /files`, `GET /files/url`).
- `service/TicketService.java` — flujo de negocio: crear un ticket con sus dos fotos.

La regla de oro que se respeta acá: **el dominio depende de una interfaz, no de MinIO**.
`TicketService` inyecta `StorageService` (la interfaz), no `MinioStorageService`.
Si mañana el storage pasa a ser S3 o un filesystem local para tests, se cambia
el adaptador y ni `TicketService` ni `FileController` se enteran.

---

## 2. El concepto central: KEY vs URL

Esto es lo más importante de todo el documento, léelo dos veces:

> **En la base de datos se guarda la KEY del objeto (`tickets/uuid.jpg`), NUNCA
> la URL presignada.**

¿Por qué? Las URLs presignadas de MinIO son firmas temporales que **expiran**
(por defecto, 1 hora — configurable en `minio.presigned-expiry-seconds`). Si
persistiéramos la URL:

- A la hora, el link queda muerto aunque el archivo siga ahí.
- Cada vez que expira habría que hacer un UPDATE en la base solo para refrescar
  un string. Eso es tratar un dato derivado como si fuera la fuente de verdad.

La key, en cambio, es estable: identifica el objeto para siempre. La URL se
**calcula on-demand** en cada lectura, con `storageService.getUrl(key)`.

```
BASE DE DATOS (permanente)          RESPUESTA HTTP (temporal, se recalcula)
┌─────────────────────────┐         ┌──────────────────────────────────────┐
│ ticket_foto_url          │  ──►    │ http://localhost:9000/tickets/       │
│ = "tickets/3f2a-8b.jpg"  │  getUrl │   3f2a-8b.jpg?X-Amz-Signature=...    │
└─────────────────────────┘         │   (válida 1 hora desde AHORA)         │
                                     └──────────────────────────────────────┘
```

> Nota de naming: las columnas de `Ticket` se llaman `ticketFotoUrl` /
> `tableroFotoUrl` (así se pidieron originalmente), pero **contienen la key**,
> no una URL. Si el nombre te confunde a futuro, vale la pena renombrarlas a
> `ticketFotoKey` / `tableroFotoKey` para que el nombre no mienta.

---

## 3. Por qué hay DOS `MinioClient`

Este es el detalle que hace que las URLs funcionen tanto en Docker como para
un cliente externo (la app mobile). Definidos en `MinioConfig`:

| Bean | Endpoint que usa | Para qué |
|------|-------------------|----------|
| `minioClient` (`@Primary`) | `minio.endpoint` → `http://minio:9000` dentro de Docker | Operaciones reales: `putObject`, `removeObject`, `bucketExists`. El backend habla con MinIO por el nombre de servicio de la red de Docker. |
| `minioPublicClient` | `minio.public-endpoint` → `http://localhost:9000` | **Solo firma** URLs presignadas, con el host público correcto. |

> **⚠️ Región obligatoria (bug que rompía la creación de tickets).** Firmar NO
> es del todo offline: la primera vez, el SDK de MinIO hace un `GetBucketLocation`
> contra el endpoint para averiguar la región. Como el cliente público apunta a
> `localhost:9000` y **desde dentro del contenedor** eso es el propio backend
> (nada escuchando), esa llamada fallaba con `Connection refused` → `POST /tickets`
> devolvía 500 al armar la respuesta. **Fix:** se fija la región explícitamente en
> ambos clientes (`.region(properties.getRegion())`, prop `minio.region`, default
> `us-east-1`), así el SDK NO hace esa llamada y la firma es 100% local.

Si usáramos un único cliente apuntando a `minio:9000`, las URLs presignadas
tendrían ese host — y `minio:9000` no significa nada fuera de la red interna
de Docker. Tu app mobile (u otro cliente externo) no podría resolverlo, y la
"exposición de URLs" pedida originalmente no serviría de nada.

Configuración relevante (`docker-compose.yml`):

```yaml
backend:
  environment:
    MINIO_ENDPOINT: http://minio:9000        # interno
    MINIO_PUBLIC_ENDPOINT: http://localhost:9000  # público
```

---

## 4. Flujo completo (crear un ticket con fotos)

```
  Cliente (mobile/Postman)
        │  multipart/form-data:
        │  litros, idPrecio, idProveedor, ticketFoto, tableroFoto
        │  + header Authorization: Bearer <jwt>
        ▼
  TicketController.create()
        │  Authentication.getName() → email del token
        ▼
  TicketService.create()
        │
        ├─ 1. resolveEmpleado(email)      ──► PersonaRepository.findByEmail
        │      (el empleado SALE DEL JWT, nunca del body — ver sección 5)
        │
        ├─ 2. Valida precio y proveedor   ──► PrecioRepository / ProveedorRepository
        │      (ANTES de subir nada: si el request es inválido, no quiero
        │       objetos huérfanos flotando en MinIO)
        │
        ├─ 3. storageService.upload(ticketFoto, "tickets")   ──► key #1
        ├─ 4. storageService.upload(tableroFoto, "tableros") ──► key #2
        │
        ├─ 5. new Ticket(); setTicketFotoUrl(key1); setTableroFotoUrl(key2)
        │      ticketRepository.save(ticket)
        │
        └─ 6. toResponse(ticket)
               ──► storageService.getUrl(key1)  URL presignada fresca
               ──► storageService.getUrl(key2)  URL presignada fresca
        ▼
  TicketResponse { ..., ticketFotoKey, ticketFotoUrl, tableroFotoKey, tableroFotoUrl }
```

Para releer un ticket ya creado (con URLs recién firmadas, no las viejas):

```
GET /tickets/{id}   ──►  TicketService.get()  ──►  misma lógica de toResponse()
```

---

## 5. Por qué el empleado sale del JWT y no del body

`JwtFilter` (ya existente, de la auth) deja el **email** como principal en el
`SecurityContext` tras validar el token. `TicketController` lo lee así:

```java
public ResponseEntity<TicketResponse> create(
        @Valid @ModelAttribute CreateTicketRequest request,
        Authentication authentication) {
    return ResponseEntity.ok(ticketService.create(request, authentication.getName()));
}
```

Si en cambio `idEmpleado` viniera en el body, cualquier usuario autenticado
podría cargar tickets a nombre de otro empleado con solo cambiar un número en
el request. Al derivarlo del token, el backend siempre sabe con certeza quién
está haciendo la carga — esa es la garantía real de la autenticación, no un
detalle cosmético.

---

## 6. Endpoints expuestos

### Genéricos de archivos (`FileController`)

```
POST /files                    multipart: file, folder (default "tickets")
                                → { objectKey, url }

GET  /files/url?key=<objectKey>
                                → { objectKey, url }   (URL fresca para una key existente)
```

### De dominio (`TicketController`)

```
POST /tickets                  multipart: litros, fechaCarga (opcional),
                                idPrecio, idProveedor, ticketFoto, tableroFoto
                                → TicketResponse

GET  /tickets/{id}
                                → TicketResponse

POST /tickets/analyze          multipart: ticketFoto  → TicketAnalysisResponse
                                (OCR + auto-registro de catálogo — ver docs/OCR.md)
```

Ambos grupos requieren JWT — `SecurityConfig` no los lista en `permitAll()`,
así que caen en la regla `anyRequest().authenticated()`.

### Solo administrador (`AdminTicketController`)

```
GET /admin/tickets             ?empleadoId= &proveedorId= &desde= &hasta=
                                &page= &size=          → PagedModel<TicketResponse>
```

- **Seguridad:** el prefijo `/admin/**` ya está restringido a
  `hasRole("ADMINISTRADOR")` en `SecurityConfig`; el controller no declara nada.
  Un empleado recibe **403**.
- **Filtros** todos opcionales y combinables (AND). Sin ninguno devuelve todos,
  paginados y ordenados por `fechaCarga` desc.
- **Implementación:** `JpaSpecificationExecutor` + `Specification` que agrega
  solo los predicados presentes (evita el `:param IS NULL OR ...`, que en Postgres
  rompe con "could not determine data type" al pasar un timestamp `null`).
- **Sin N+1:** la `Specification` hace `JOIN FETCH` de `empleado`, `proveedor` y
  `precio` en la query de datos (no en la de count), así el listado se resuelve
  en **una sola query** en vez de una consulta por fila.

---

## 7. Configuración (`application.yml`)

```yaml
minio:
  endpoint: ${MINIO_ENDPOINT:http://localhost:9000}          # interno
  public-endpoint: ${MINIO_PUBLIC_ENDPOINT:...}               # público, para firmar
  access-key: ${MINIO_ACCESS_KEY}
  secret-key: ${MINIO_SECRET_KEY}
  bucket: ${MINIO_BUCKET:tickets}
  presigned-expiry-seconds: ${MINIO_PRESIGNED_EXPIRY:3600}    # 1 hora por defecto
  region: ${MINIO_REGION:us-east-1}                           # evita GetBucketLocation al firmar
```

El bucket se crea solo si no existe: `MinioStorageService.ensureBucketExists()`
corre en `@PostConstruct`, al arrancar la aplicación.

---

## 8. Archivos tocados / creados

**Creados**
- `config/MinioProperties.java` — propiedades `minio.*`.
- `config/MinioConfig.java` — beans `minioClient` y `minioPublicClient`.
- `service/StorageService.java` — interfaz de storage.
- `service/MinioStorageService.java` — implementación contra MinIO.
- `exception/StorageException.java`.
- `dto/response/FileUploadResponse.java`.
- `controller/FileController.java`.
- `dto/request/CreateTicketRequest.java`.
- `dto/response/TicketResponse.java`.
- `service/TicketService.java`.
- `controller/TicketController.java`.
- `docs/STORAGE.md` — este documento.

**Modificados**
- `backend/pom.xml` — dependencia `io.minio:minio:8.5.17`.
- `backend/src/main/resources/application.yml` — `minio.public-endpoint` y
  `minio.presigned-expiry-seconds`.
- `docker-compose.yml` — `MINIO_ENDPOINT` / `MINIO_PUBLIC_ENDPOINT` en el
  servicio `backend`.
- `model/Ticket.java` — atributos `ticketFotoUrl` / `tableroFotoUrl` (guardan
  la key, ver sección 2).

---

## 9. Caveats conocidos (no resueltos todavía)

1. **MinIO no es transaccional con Postgres.** Si `ticketRepository.save()`
   fallara *después* de subir las dos fotos, quedarían objetos huérfanos en
   MinIO (el ticket no se crea, pero los archivos ya están subidos). No hay
   compensación (borrado en rollback) implementada. Para producción,
   convendría envolver el `save` en un `try/catch` que llame a
   `storageService.delete(key)` de ambas fotos ante cualquier excepción
   posterior a la subida.
2. **Naming de columnas engañoso.** Como se explicó en la sección 2, las
   columnas `ticket_foto_url` / `tablero_foto_url` guardan keys, no URLs. Es
   así porque se pidieron con ese nombre originalmente; un rename a `..._key`
   sería más honesto pero requiere migración.
3. **Manejo de errores** (RESUELTO). `TicketService` ya no lanza
   `RuntimeException` cruda: usa excepciones de dominio que el
   `GlobalExceptionHandler` mapea a JSON — `ResourceNotFoundException` → **404**
   (precio/proveedor/ticket/usuario inexistente) y `ForbiddenException` → **403**
   (usuario que no es empleado). Detalle en `docs/ERRORS.md`.
