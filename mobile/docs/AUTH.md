# Autenticación (mobile)

Esta guía explica cómo está armada la autenticación en la app móvil: el flujo
completo de login/registro, la persistencia del token, y la redirección por rol.
La idea es que puedas entender la estructura sin tener que adivinar nada.

---

## 1. Visión general

La autenticación se apoya en cuatro piezas, cada una con UNA responsabilidad:

| Pieza | Archivo | Responsabilidad |
|-------|---------|-----------------|
| Cliente HTTP | `services/api.ts` | Hacer `fetch`, adjuntar el token, manejar errores. No sabe de auth. |
| Capa de API de auth | `services/auth.ts` | Traducir login/registro a rutas HTTP tipadas. No sabe de React. |
| Estado de sesión | `context/AuthContext.tsx` | Guardar/rehidratar/limpiar la sesión. Única fuente de verdad de "quién está logueado". |
| Guardia de navegación | `app/_layout.tsx` + `app/index.tsx` | Redirigir según haya sesión y según el rol. |

Y dos pantallas que consumen todo eso: `app/(auth)/login.tsx` y `app/(auth)/register.tsx`.

La regla de oro que se respeta acá: **cada capa habla solo con la de al lado**.
La pantalla no hace `fetch`; le pide a `services/auth`. `services/auth` no toca
React; le pide a `services/api`. El estado global vive en un solo lugar.

---

## 2. El contrato con el backend (la fuente de verdad)

Todo el front está modelado para hablar EXACTAMENTE igual que el backend Spring Boot:

```
POST /auth/login      Body: { email, password }
POST /auth/register   Body: { nombre, apellido, documento, email, password, rol }

Respuesta (ambos): AuthResponse
{ token, nombre, apellido, email, rol }
```

Dos detalles que importan y que el front respeta al pie de la letra:

1. **El rol viene en MAYÚSCULAS**: `"EMPLEADO"` o `"ADMINISTRADOR"`. El backend
   serializa el enum `Rol` por su `.name()`. Si el front comparara contra
   `"empleado"` (minúsculas), la redirección nunca funcionaría.
2. **El puerto del backend es `8080`** (default de Spring Boot), no `3000`.

---

## 3. Flujo completo (login)

```
  Pantalla login.tsx
        │  email + password
        ▼
  services/auth.ts  ──►  loginRequest()
        │                     │
        ▼                     ▼
  services/api.ts  ──►  POST http://<host>:8080/auth/login
        │
        ▼
  AuthResponse { token, nombre, apellido, email, rol }
        │
        ▼
  AuthContext.login(res)  ──►  guarda en AsyncStorage + setUser(res)
        │
        ▼
  Cambia `user`  ──►  el useEffect de app/_layout.tsx redirige
        │
        ├─ rol EMPLEADO        ──►  /(empleado)
        └─ rol ADMINISTRADOR   ──►  /(administrador)
```

Clave: **las pantallas NO navegan a mano** después del login. Solo persisten la
sesión. El guardia del layout raíz observa el cambio de `user` y redirige. Esto
centraliza TODA la lógica de "a dónde va cada quién" en un único lugar.

El registro funciona igual, pero además hace **auto-login**: como el backend ya
devuelve un token en el registro, persistimos la sesión apenas se crea la cuenta.

---

## 4. Persistencia del token

- Se usa `AsyncStorage` (almacenamiento clave-valor persistente del dispositivo).
- La sesión se guarda bajo la clave `'user'` como JSON: `{ token, nombre, apellido, email, rol }`.
- Al abrir la app, `AuthProvider` rehidrata esa sesión en un `useEffect`. Mientras
  lee el storage, `isLoading` está en `true` para evitar redirecciones prematuras
  (si redirigiéramos antes de saber si hay sesión, veríamos un parpadeo al login).
- `services/api.ts` lee esa MISMA clave `'user'` para sacar el token y mandarlo
  en el header `Authorization: Bearer <token>` en cada request autenticado.

> Nota de seguridad: `AsyncStorage` no está cifrado. Para datos sensibles en
> producción conviene migrar a `expo-secure-store`. Para este TP/MVP está bien.

---

## 5. Redirección por rol

Vive en dos archivos que se complementan:

- **`app/index.tsx`** (ruta `/`): decide el primer destino al entrar. Si no hay
  sesión → login; si hay → al grupo del rol. Evita el parpadeo en el arranque.
- **`app/_layout.tsx`**: es el guardia permanente. En cada cambio de ruta o de
  sesión revisa: ¿el usuario está donde le corresponde? Si no, lo reubica. Esto
  cubre casos como cerrar sesión estando adentro, o intentar entrar a una sección
  que no es de tu rol.

Los grupos de ruta de Expo Router son carpetas entre paréntesis:

```
app/(auth)/            ← login y registro
app/(empleado)/        ← sección del rol EMPLEADO
app/(administrador)/   ← sección del rol ADMINISTRADOR
```

Los paréntesis significan "grupo": organizan archivos SIN agregar segmento a la
URL. `segments[0]` en el código nos dice en qué grupo estamos parados.

---

## 6. Convención de Expo Router que hay que respetar

- El layout de un grupo/raíz se llama **`_layout.tsx`** (con guion bajo). Sin el
  guion, Expo Router lo trata como una pantalla común, no como layout. Si el
  layout raíz no se reconoce, el `AuthProvider` no envuelve la app y `useAuth()`
  devuelve vacío: todo se rompe en silencio.
- `index.tsx` dentro de un grupo es la pantalla por defecto de ese grupo.
- El entry real de la app es `expo-router/entry` (ver `package.json` → `main`).

---

## 7. Archivos tocados / creados

**Creados**
- `services/auth.ts` — capa de API de auth (tipos + `loginRequest` / `registerRequest`).
- `app/(auth)/register.tsx` — pantalla de registro con selector de rol.
- `app/_layout.tsx` — layout raíz correcto (monta `AuthProvider` + guardia).
- `app/(empleado)/_layout.tsx` y `app/(empleado)/index.tsx` — sección empleado.
- `app/(administrador)/_layout.tsx` y `app/(administrador)/index.tsx` — sección admin.
- `docs/AUTH.md` — este documento.

**Modificados**
- `context/AuthContext.tsx` — alineado al backend (`rol` en mayúsculas, campos
  `nombre/apellido/email`; `login` ahora recibe el `AuthResponse` completo).
- `services/api.ts` — `BASE_URL` por defecto corregido a `:8080`.
- `app/(auth)/login.tsx` — de placeholder a formulario funcional.
- `app/index.tsx` — redirección por rol corregida (mayúsculas + campo `rol`).

**Eliminados (estaban rotos / muertos)**
- `app/layout.tsx` → se renombró a `app/_layout.tsx` (faltaba el guion bajo).
- `app/(employee)/` y `app/(manager)/` → renombrados a `(empleado)`/`(administrador)`
  para coincidir con los valores de rol del backend; además `(manager)/layout.tsx`
  tampoco tenía el guion bajo.
- `index.ts` (raíz) → código muerto del template clásico de Expo. El entry real es
  `expo-router/entry`, y este archivo importaba un `./App` inexistente, lo que
  rompía el typecheck por colisión de mayúsculas con la carpeta `app/`.

---

## 8. Cómo configurar la URL del backend

Por defecto el front apunta a `http://localhost:8080`. Eso funciona en el
emulador corriendo en la misma máquina que el backend, pero **en un celular
físico `localhost` es el propio teléfono**, no tu PC.

Definí la IP de tu máquina en una variable de entorno antes de levantar Expo:

```bash
EXPO_PUBLIC_API_URL=http://192.168.0.10:8080 npm start
```

(Reemplazá `192.168.0.10` por la IP de tu PC en la red local.)

---

## 9. Un caveat del backend (no es del front)

Hoy el backend lanza `RuntimeException` ante credenciales inválidas. Spring lo
mapea a HTTP 500 y, por defecto, **oculta el mensaje** de la excepción. Eso
significa que el front mostrará un error genérico en vez de "Email o contraseña
incorrectos". Cuando quieras mensajes claros, conviene en el backend lanzar una
excepción que mapee a 401 (con un `@ControllerAdvice`) o habilitar
`server.error.include-message=always`. El front ya está preparado: lee
`error.message` de la respuesta apenas el backend lo exponga.
