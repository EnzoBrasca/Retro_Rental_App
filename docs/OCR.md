# OCR de tickets con Mistral

Analiza la foto de un ticket de carga de combustible y extrae sus campos
(litros, fecha/hora, importe total, precio por litro y estación) para que el
empleado no tenga que cargarlos a mano. La extracción usa el endpoint de OCR de
**Mistral** (`POST /v1/ocr`) con salida estructurada vía JSON schema, y además
resuelve contra el catálogo local (proveedor y precio vigente) para pre-cargar
el formulario de creación de ticket.

## Componentes

| Archivo | Rol |
| --- | --- |
| `controller/TicketController.java` | Expone `POST /tickets/analyze`. |
| `service/TicketAnalysisService.java` | Interfaz. Define `analyze(MultipartFile) -> TicketAnalysisResult`. |
| `service/MistralTicketAnalysisService.java` | Implementación del OCR sobre Mistral. |
| `service/TicketService.java` | Orquesta el análisis + el match contra el catálogo. |
| `config/MistralProperties.java` | Propiedades de configuración (`mistral.*`). |
| `config/MistralConfig.java` | Crea el `RestClient` que habla con Mistral. |
| `dto/response/TicketAnalysisResult.java` | Datos crudos leídos por el OCR. |
| `dto/response/TicketAnalysisResponse.java` | Respuesta del endpoint: OCR + IDs resueltos del catálogo. |
| `exception/TicketAnalysisException.java` | Error propio del análisis (→ 503). |
| `exception/GlobalExceptionHandler.java` | Traduce `TicketAnalysisException` a HTTP 503 con JSON. |

## Flujo completo

```
POST /tickets/analyze (multipart "ticketFoto")
        │
        ▼
TicketService.analyze()
        │
        ├─► MistralTicketAnalysisService.analyze()  → OCR de Mistral → TicketAnalysisResult
        │        (litros, fechaCarga, importeTotal, precioPorLitro, estacion)
        │
        ├─► matchProveedor(estacion)                → Proveedor (si hay match único) o null
        │
        └─► precioRepository.findByServicioAndFechaHastaIsNull(COMBUSTIBLE) → Precio vigente o null
        │
        ▼
TicketAnalysisResponse  (se devuelve al cliente; NADA se persiste)
```

El endpoint **no crea el ticket** (eso sigue siendo `POST /tickets`), pero desde
la incorporación del auto-registro **sí escribe en el catálogo**: si el OCR lee
un proveedor o un precio que no existe, los da de alta (ver "Auto-registro" más
abajo). Por eso `TicketService.analyze` es `@Transactional` de escritura, no
`readOnly`.

### Cómo funciona el OCR (`MistralTicketAnalysisService`)

1. **Entrada.** Recibe la foto como `MultipartFile`. Valida que no esté vacía.

2. **Imagen → data URI.** La foto se codifica en base64 y se arma un data URI
   (`data:image/jpeg;base64,...`). Si el `contentType` no es una imagen válida,
   cae a `image/jpeg` por defecto.

3. **Request al OCR.** Se hace `POST /v1/ocr` con este cuerpo:

   ```json
   {
     "model": "mistral-ocr-latest",
     "document": {
       "type": "image_url",
       "image_url": "data:image/jpeg;base64,..."
     },
     "document_annotation_format": {
       "type": "json_schema",
       "json_schema": {
         "name": "ticket_carga",
         "schema": {
           "type": "object",
           "properties": {
             "litros":         { "type": ["number", "null"] },
             "fechaCarga":     { "type": ["string", "null"] },
             "importeTotal":   { "type": ["number", "null"] },
             "precioPorLitro": { "type": ["number", "null"] },
             "estacion":       { "type": ["string", "null"] },
             "cuit":           { "type": ["string", "null"] }
           },
           "required": ["litros", "fechaCarga", "importeTotal", "precioPorLitro", "estacion", "cuit"],
           "additionalProperties": false
         }
       }
     }
   }
   ```

   El OCR "pelado" devolvería solo markdown. Con `document_annotation_format` se
   le pasa un **JSON schema** y Mistral devuelve los campos ya estructurados en
   el campo `document_annotation` de la respuesta. Una sola llamada, sin parsear
   texto a mano.

4. **Parseo de la respuesta.** Se lee `document_annotation` (que puede venir como
   string JSON o como objeto) y se mapea a `TicketAnalysisResult`.

### Resolución contra el catálogo + Auto-registro (`TicketService`)

El OCR devuelve texto libre (`estacion`, `cuit`) y números, pero un `Ticket`
necesita FKs a `Proveedor` y `Precio`. El backend cierra ese hueco resolviendo
—y creando si hace falta— las entradas de catálogo:

- **Proveedor (`resolveProveedor`).** Primero busca un proveedor de
  `COMBUSTIBLE` cuyo `nombre` coincida (por contención, sin distinguir
  mayúsculas) con `estacion`, y solo lo usa si el match es **único**.
  Si no hay match **y** el OCR trae `cuit`, **da de alta el proveedor
  automáticamente** (`nombre=estacion`, `cuit`, `servicio=COMBUSTIBLE`). Es
  idempotente: si ya existe uno con ese CUIT, lo reutiliza (`findByCuit`). Sin
  `cuit` **no** crea (la columna es `NOT NULL`): devuelve `null` para carga
  manual.
- **Precio (`resolvePrecio`).** Busca un precio de `COMBUSTIBLE` cuyo
  `precioUnitario` coincida con `precioPorLitro` (comparación por valor, ignora
  la escala). Si no existe, **crea uno nuevo como vigente** (`fechaDesde` = fecha
  del ticket, `fechaHasta = null`) y **cierra el vigente anterior** poniéndole
  `fechaHasta`, para mantener la invariante de **un único precio vigente por
  servicio**. Sin `precioPorLitro` cae al vigente actual
  (`findByServicioAndFechaHastaIsNull`).

> **⚠️ Consecuencia a tener presente.** `analyze` es un preview que el empleado
> puede abandonar, pero el alta de catálogo ocurre igual. Y si el OCR **mislee**
> un precio, genera una versión de precio nueva y cierra la vigente. Es el
> trade-off aceptado para que el formulario mobile venga con FKs reales. Si en
> el futuro molesta, la alternativa es mover el auto-registro al momento de
> **confirmar** el ticket (`POST /tickets`) en vez de al analizar.

> **Catálogo vacío.** Con el auto-registro, un ticket con `estacion`+`cuit` y
> `precioPorLitro` legibles **crea** su proveedor y su precio, así que
> `idProveedor`/`idPrecio` ya vienen resueltos aunque el catálogo estuviera
> vacío. Solo quedan en `null` cuando faltan esos datos en el ticket (ej. sin
> `cuit` no se puede crear proveedor).

## Contrato del endpoint

`POST /tickets/analyze` · `multipart/form-data` · requiere JWT de un empleado.

- **Parte multipart:** `ticketFoto` (la imagen).
- **Respuesta 200 (`TicketAnalysisResponse`):**

  ```json
  {
    "litros": 78.46,
    "fechaCarga": "2026-07-01T09:31:00",
    "importeTotal": 190583.96,
    "precioPorLitro": 2429.0,
    "estacion": "BIFUEL SRL",
    "idProveedor": null,
    "proveedorNombre": null,
    "idPrecio": null,
    "precioUnitario": null
  }
  ```

- **503 (`ApiError`):** el OCR falló o no está configurado (falta la API key).

Cualquier campo puede venir `null`: entonces la app lo deja vacío para que el
empleado lo complete antes de confirmar.

## Diseño defensivo

- **Campos opcionales.** Cada propiedad del schema admite `null` (tipo union
  `["number","null"]` / `["string","null"]`). Un ticket borroso hace que un
  campo venga `null` — es esperado, no un error.
- **`required` + `additionalProperties:false`, pero SIN `strict`.** Ver el
  gotcha de abajo: esta combinación es la que fuerza a Mistral a devolver JSON
  bien formado sin perder la posibilidad de `null`.
- **Fechas tolerantes.** `fechaCarga` se intenta como `LocalDateTime`, luego
  como `OffsetDateTime` (tolera un `Z`/offset al final, ej. `...09:31:00Z`), y
  por último como `LocalDate` a las 00:00. Si nada parsea, queda `null`.
- **Parseo resiliente.** `document_annotation` se acepta como string JSON o como
  objeto ya parseado.
- **Errores envueltos.** Todo fallo (red, lectura, interpretación) se envuelve
  en `TicketAnalysisException`, que el `GlobalExceptionHandler` traduce a un
  **503** con cuerpo `{ "status": 503, "message": "..." }`.

## ⚠️ Gotcha importante: `strict: true` rompe la anotación

En `/v1/ocr` con `document_annotation_format`, **NO usar `"strict": true`** en el
wrapper `json_schema`. Con este OCR, `strict:true` hace que Mistral devuelva
`document_annotation` como **JSON malformado** (comillas escapadas de más en las
claves); al parsearlo solo se rescata algún campo suelto y el resto queda `null`
aunque el OCR haya leído los datos correctamente.

La combinación que produce JSON válido **y** permite `null` es:

- **sin** `strict`,
- tipos union `["number","null"]` / `["string","null"]`,
- `required` con todos los campos,
- `additionalProperties: false`.

Esto está documentado en los comentarios de `MistralTicketAnalysisService` para
que nadie vuelva a agregar `strict` "para reforzar el schema".

## Arranque condicional

Tanto el `RestClient` (`MistralConfig`) como el servicio de OCR
(`MistralTicketAnalysisService`) están anotados con
`@ConditionalOnProperty(prefix = "mistral", name = "api-key")`.

Si no hay API key, esos beans **no se crean** y la app arranca igual, con el
análisis inactivo. Por eso `TicketService` inyecta el servicio de análisis con
`ObjectProvider<TicketAnalysisService>`: **crear tickets NO depende de la key**;
solo el endpoint `/analyze` la necesita, y si falta responde 503.

## Configuración

Propiedades bajo el prefijo `mistral` (en `application.yml`), cada una leída de
una variable de entorno:

| Propiedad | Variable de entorno | Default | Descripción |
| --- | --- | --- | --- |
| `mistral.api-key` | `MISTRAL_API_KEY` | *(vacío)* | API key de Mistral. Sin ella el análisis queda inactivo. |
| `mistral.base-url` | `MISTRAL_BASE_URL` | `https://api.mistral.ai` | Host base de la API. |
| `mistral.model` | `MISTRAL_MODEL` | `mistral-ocr-latest` | Modelo de OCR. |
| `mistral.timeout-seconds` | `MISTRAL_TIMEOUT_SECONDS` | `30` | Timeout de conexión y lectura, en segundos. |

---

## Guía paso a paso: activar el OCR con tu API key

### 1. Obtené tu API key

1. Entrá a [console.mistral.ai](https://console.mistral.ai) y creá una cuenta (o
   iniciá sesión).
2. En el menú, andá a **API Keys**.
3. Creá una nueva key y **copiala** (solo se muestra una vez).
4. Asegurate de tener un plan/billing activo que habilite el uso del OCR.

### 2. Configurá la variable de entorno

Elegí **una** de estas opciones.

**Opción A — variable de entorno en tu shell (desarrollo local):**

```bash
export MISTRAL_API_KEY="tu-api-key-aca"
```

**Opción B — archivo `.env` / `docker-compose`:**

El backend ya está cableado para leerla desde el entorno. En `docker-compose.yml`
el servicio del backend ya incluye:

```yaml
    environment:
      MISTRAL_API_KEY: ${MISTRAL_API_KEY}
```

y en tu `.env` (que **no** debe commitearse):

```
MISTRAL_API_KEY=tu-api-key-aca
```

> **Nunca** pongas la key directamente en `application.yml` ni la subas al repo.

### 3. (Opcional) Ajustá el resto de la config

```bash
export MISTRAL_MODEL="mistral-ocr-latest"     # otro modelo de OCR
export MISTRAL_TIMEOUT_SECONDS="45"           # timeout más largo
```

### 4. Levantá el backend

```bash
# Local
cd backend && ./mvnw spring-boot:run

# Docker (reconstruye la imagen para tomar cambios de código)
docker compose up -d --build backend
```

Con la key presente, Spring crea el `RestClient` de Mistral y el
`MistralTicketAnalysisService` queda activo automáticamente.

---

## Verificación manual

Necesitás una **foto de un ticket de combustible real** (JPG/PNG). Con una
imagen que no sea de combustible, los campos específicos (`litros`, `estacion`,
`precioPorLitro`) van a venir `null` — no es un error, es que no hay qué leer.

### Nivel 1 — La API de Mistral directo (sin el backend)

Prueba que la key es válida y que el OCR lee:

```bash
KEY=$(rg -o 'MISTRAL_API_KEY=.*' .env | sed 's/MISTRAL_API_KEY=//')
IMG=/ruta/a/tu/ticket.jpg
B64=$(base64 -i "$IMG")

curl -s https://api.mistral.ai/v1/ocr \
  -H "Authorization: Bearer $KEY" \
  -H "Content-Type: application/json" \
  -d "{\"model\":\"mistral-ocr-latest\",\"document\":{\"type\":\"image_url\",\"image_url\":\"data:image/jpeg;base64,$B64\"}}" | jq .
```

- **200** con `pages[].markdown` conteniendo el texto del ticket → la key y el
  OCR funcionan.
- **401** → key inválida o mal copiada.
- **400 `invalid_request_file`** → la imagen no es válida (formato o corrupción).

### Nivel 2 — A través del backend (flujo real)

Prueba la integración completa (OCR + mapeo + match de catálogo):

```bash
# 1. Token de un empleado
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"empleado@example.com","password":"secret123"}' \
  | rg -o '"token":"[^"]+"' | sed 's/"token":"//;s/"//')

# 2. Análisis
curl -s -X POST http://localhost:8080/tickets/analyze \
  -H "Authorization: Bearer $TOKEN" \
  -F "ticketFoto=@/ruta/a/tu/ticket.jpg;type=image/jpeg" | jq .
```

- `litros`, `fechaCarga`, `precioPorLitro`, `estacion` con valores coherentes →
  el mapeo OCR→DTO anda.
- `idProveedor`/`idPrecio` en `null` hasta que haya catálogo `COMBUSTIBLE`.
- **503** → problema con el OCR (key o imagen); el `message` lo aclara.

### Resultado verificado

Probado con un ticket real (BIFUEL SRL, DIESEL X10 — `78,46 x 2429,00 = 190583,96`,
fecha `01/07/2026 09:31`), el endpoint devolvió exactamente:

```json
{
  "litros": 78.46,
  "fechaCarga": "2026-07-01T09:31:00",
  "importeTotal": 190583.96,
  "precioPorLitro": 2429.0,
  "estacion": "BIFUEL SRL"
}
```

---

## Pendientes conocidos

- **Flujo mobile:** falta la UI (cámara → `analyze` → formulario pre-cargado →
  confirmar → `POST /tickets`).
- **ABM de catálogo:** el auto-registro cubre el alta desde el OCR, pero no hay
  una pantalla para que el admin gestione proveedores/precios a mano.
- **Precio vigente único:** `findByServicioAndFechaHastaIsNull` asume que hay un
  solo precio vigente por servicio. `resolvePrecio` mantiene esa invariante
  (cierra el vigente anterior al crear uno nuevo), pero si ya hubiera datos con
  dos vigentes, tira `NonUniqueResultException`.
- **`analyze` escribe en un preview:** ver la advertencia en "Auto-registro".
