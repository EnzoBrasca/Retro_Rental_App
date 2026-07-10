# Manejo de errores (backend)

Cómo el backend traduce las excepciones a respuestas HTTP con cuerpo JSON, y la
historia del **403 espurio** que durante un tiempo enmascaró todos los errores.

---

## 1. El modelo: `GlobalExceptionHandler` + `ApiError`

Un único `@RestControllerAdvice` (`exception/GlobalExceptionHandler.java`)
centraliza la traducción de excepciones a HTTP. Todas las respuestas de error
comparten el mismo cuerpo, el record `ApiError`:

```json
{ "status": 404, "message": "No existe el precio indicado" }
```

El front (mobile, `services/api.ts`) lee `error.message` para mostrarle el texto
real al usuario.

| Excepción | Status | Cuándo |
|-----------|--------|--------|
| `InvalidCredentialsException` | **401** | Login con email/contraseña incorrectos. |
| `ForbiddenException` | **403** | Acción no permitida para el rol (ej. un no-empleado cargando tickets). |
| `ResourceNotFoundException` | **404** | Recurso referenciado inexistente (precio, proveedor, ticket, usuario). |
| `ConflictException` | **409** | Alta duplicada (email o documento ya registrados). |
| `MethodArgumentNotValidException` | **400** | Falla de validación `@Valid`; los mensajes de cada campo se unen con `; `. |
| `TicketAnalysisException` | **503** | Falla del OCR de Mistral o servicio no configurado (falta la API key). |

Regla de diseño: **los servicios NO lanzan `RuntimeException` cruda**. Cada caso
de negocio tiene su excepción de dominio (en `exception/`), y el handler decide
el status. Así el status vive en un solo lugar y las respuestas son consistentes.

---

## 2. La causa raíz del 403 espurio (bug histórico, ya resuelto)

Durante un tiempo, login y registro devolvían **403** desde la app, incluso con
datos válidos. La causa NO era de seguridad de las rutas de auth (que sí estaban
en `permitAll`), sino esta cadena:

```
1. POST /auth/login  →  pasa la seguridad  →  llega al controller
2. El controller/servicio lanza una excepción (validación o negocio)
3. Spring MVC hace un FORWARD interno a /error para renderizar el error
4. Spring Security vuelve a correr la cadena de filtros sobre /error
5. /error NO estaba en permitAll  →  anyRequest().authenticated()
   →  usuario anónimo  →  403
6. El error real (500/400) queda enmascarado como un 403 mudo
```

Lo mismo pasaba con cualquier fallo de validación `@Valid`. **Fix (doble):**

1. **`/error` agregado a `permitAll`** en `SecurityConfig`, para que el forward
   interno no lo bloquee la seguridad.
2. **`GlobalExceptionHandler`**, que resuelve la excepción en el MISMO dispatch
   (sin forward a `/error`) y devuelve el status correcto con JSON.

```java
.requestMatchers("/auth/**", "/health", "/error").permitAll()
```

> Lección: en un backend stateless con Spring Security, si un endpoint público
> lanza una excepción y `/error` no es público, vas a ver **403 en vez del error
> real**. Es un clásico contraintuitivo.

---

## 3. Gotcha de infraestructura: Jackson 3 en Spring Boot 4

No es de HTTP, pero es un error de arranque que costó diagnosticar y conviene
dejar documentado porque **afecta a todo bean que toque JSON**.

Spring Boot **4.1** migró de Jackson 2 (`com.fasterxml.jackson`) a **Jackson 3**
(`tools.jackson`). El `ObjectMapper` autoconfigurado es
`tools.jackson.databind.ObjectMapper`.

- Importar el `ObjectMapper` de **Jackson 2** **compila** (está en el classpath
  de rebote vía `jjwt-jackson`) pero **no existe como bean** → el contexto no
  arranca (`UnsatisfiedDependency`).
- **Regla:** para JSON usar SIEMPRE `tools.jackson.*`, nunca `com.fasterxml.jackson.*`.
- En Jackson 3, `readTree` lanza `tools.jackson.core.JacksonException` (unchecked),
  ya **no** `java.io.IOException`.

---

## 4. Archivos

- `exception/GlobalExceptionHandler.java` — el `@RestControllerAdvice`.
- `dto/response/ApiError.java` — cuerpo estándar `{ status, message }`.
- `exception/` — excepciones de dominio: `InvalidCredentialsException`,
  `ConflictException`, `ResourceNotFoundException`, `ForbiddenException`,
  `TicketAnalysisException`, `StorageException`.
- `config/SecurityConfig.java` — `/error` en `permitAll` (ver sección 2).
