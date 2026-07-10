# Despliegue y estructura de Docker Compose

Esta guía explica cómo está armado el `docker-compose` del proyecto, **por qué**
está separado en varios archivos, y cómo llevar el backend a producción en un
servidor externo sin agujeros de seguridad.

---

## 1. Visión general

El stack son tres servicios de aplicación más, en producción, un reverse proxy:

| Servicio   | Qué es                            | Puerto interno |
|------------|-----------------------------------|----------------|
| `backend`  | La API Spring Boot                | 8080           |
| `postgres` | Base de datos                     | 5432           |
| `minio`    | Almacenamiento de imágenes (S3)   | 9000 / 9001    |
| `nginx`    | Reverse proxy (SOLO en prod)      | 80 / 443       |

La configuración se reparte en **cuatro archivos**, cada uno con UNA
responsabilidad:

| Archivo | Rol | Cuándo se aplica |
|---------|-----|------------------|
| `docker-compose.yml` | **Base**: qué servicios existen y cómo se hablan entre ellos. Seguro por defecto: no publica NADA al host. | Siempre (combinado con uno de los de abajo). |
| `docker-compose.override.yml` | **Dev**: puertos abiertos al host + `.env`. | Automático con `docker compose up`. |
| `docker-compose.prod.yml` | **Prod**: reverse proxy, `restart`, `.env.production`, cero puertos directos. | Explícito con `-f`. |
| `infra/nginx/conf.d/app.conf` | Config del reverse proxy (rutas por subdominio + TLS). | Solo en prod. |

La regla de oro: **el archivo base es "seguro por defecto"**. Solo el entorno de
desarrollo abre puertos por comodidad; producción los mantiene cerrados y expone
únicamente el proxy.

---

## 2. Cómo se usa

### Desarrollo (tu máquina)

```bash
docker compose up -d --build
```

Compose mergea **automáticamente** `docker-compose.yml` + `docker-compose.override.yml`.
Resultado: `backend:8080`, `postgres:5432` y `minio:9000/9001` quedan publicados
en `localhost` para que les pegues directo (Postman, pgAdmin, la consola de MinIO,
o correr `contextLoads` desde el host — ver `docs/backend-session-*` y el README de tests).

### Producción (servidor externo)

```bash
# 1. Crear el archivo de secretos de prod (una sola vez)
cp .env.production.example .env.production
# ...editar .env.production con valores REALES y fuertes...

# 2. Levantar el stack de producción
docker compose --env-file .env.production \
  -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

Al pasar los `-f` explícitos, Compose **ignora** el `override.yml` de dev y usa
el `prod.yml` en su lugar.

---

## 3. Por qué postgres y minio NO exponen puertos al host

Este es el punto más importante de seguridad.

En el archivo base, `postgres` y `minio` **no tienen sección `ports:`**. Eso
significa que sus puertos existen SOLO dentro de la red interna de Docker. El
`backend` los alcanza por **nombre de servicio**, no por `localhost`:

```
DB_HOST=postgres            # no "localhost"
MINIO_ENDPOINT=http://minio:9000
```

**¿Por qué?** Publicar `5432` o `9000` al host significa abrirlos en la interfaz
de red del servidor. En un server con IP pública, eso deja la base de datos y el
storage accesibles desde internet — un ataque directo, saltándose por completo la
autenticación de la API. Manteniéndolos sin publicar, la única forma de tocarlos
es estar DENTRO de la red de Docker, es decir, ser el propio backend.

En **desarrollo** sí los abrimos (en `override.yml`), pero ahí la interfaz es tu
`localhost`, no una IP pública, así que el riesgo es nulo y la comodidad alta.

---

## 4. Por qué el backend tampoco se expone directo en prod

En `prod.yml`, el `backend` **no publica el 8080**. El único servicio con puertos
al host es `nginx` (80/443). El backend queda accesible solo desde la red interna,
y nginx le reenvía el tráfico.

**¿Por qué un reverse proxy adelante?**

1. **TLS/HTTPS**: nginx termina el SSL. El backend habla HTTP plano puertas
   adentro; el cifrado vive en el borde. El mobile le pega a `https://api.tuapp.com`.
2. **Un solo punto de entrada**: toda la superficie pública es el proxy. Más fácil
   de asegurar, monitorear y ponerle rate-limiting.
3. **Dominio en vez de IP:puerto**: el mobile apunta a un dominio estable
   (`api.tuapp.com`), no a `123.45.67.89:8080`. Podés mover el server o escalar sin
   tocar la app.

```
                         ┌─────────── red interna de Docker ───────────┐
  mobile ──HTTPS──▶ nginx ──HTTP──▶ backend ──▶ postgres
                    (80/443)           │
                                       └──────▶ minio
  mobile ──HTTPS──▶ nginx ─────────────────────▶ minio  (descarga de fotos)
```

---

## 5. El caso especial de MinIO: `MINIO_PUBLIC_ENDPOINT`

MinIO tiene **dos endpoints** y es fácil equivocarse:

- `MINIO_ENDPOINT` (`http://minio:9000`): lo usa el backend para **operar** contra
  MinIO (subir/borrar). Es interno, por la red de Docker. Constante en todo entorno.
- `MINIO_PUBLIC_ENDPOINT`: el host con el que se **firman las URLs presignadas**
  que el mobile usa para **descargar** las fotos. Tiene que ser un host que el
  celular pueda alcanzar de verdad.

| Entorno | `MINIO_PUBLIC_ENDPOINT` | Dónde se setea |
|---------|-------------------------|----------------|
| Dev     | `http://localhost:9000` | `override.yml` |
| Prod    | `https://files.tuapp.com` | `.env.production` |

**Si en producción dejás `localhost`, el mobile nunca va a poder descargar una
foto**: la URL presignada apuntaría a la máquina del celular, no al server. Por eso
en prod se rutea un subdominio (`files.tuapp.com`) hacia `minio:9000` vía nginx, y
ese es el valor de `MINIO_PUBLIC_ENDPOINT`.

nginx reenvía el `Host` original a MinIO para que la firma de la URL valide bien
(ver `infra/nginx/conf.d/app.conf`).

---

## 6. Certificados TLS

nginx monta los certificados desde `infra/nginx/certs/` (montado read-only). Esos
archivos **NO se commitean** (solo hay un `.gitkeep`). Nombres esperados por
`app.conf`:

```
infra/nginx/certs/api.fullchain.pem
infra/nginx/certs/api.privkey.pem
infra/nginx/certs/files.fullchain.pem
infra/nginx/certs/files.privkey.pem
```

Opciones para obtenerlos:
- **Let's Encrypt** (recomendado): usá `certbot` en el host o un contenedor
  companion, y montá los `.pem` resultantes en `infra/nginx/certs/`.
- **Certificado propio de la organización**: copiá los `.pem` con esos nombres.

---

## 7. Variables de entorno

Todos los secretos y config viven en archivos `.env` (gitignoreados):

- **Dev** → `.env` (raíz). Ya lo tenés.
- **Prod** → `.env.production` (raíz). Copialo de `.env.production.example` (también en la raíz).

Variables que **fija docker-compose** y NO debés poner en los `.env`:

| Variable | Valor | Por qué |
|----------|-------|---------|
| `DB_HOST` | `postgres` | Routing interno por nombre de servicio. |
| `DB_PORT` | `5432` | Puerto interno de postgres. |
| `MINIO_ENDPOINT` | `http://minio:9000` | Endpoint interno de MinIO. |

Si las definieras en el `.env` con otro valor (ej. `DB_HOST=localhost`), romperías
el routing interno. Por eso el compose las impone en el bloque `environment`.

---

## 8. Checklist de producción

Antes de exponer el server:

- [ ] `.env.production` creado con **passwords fuertes** (DB, MinIO) y un
      `JWT_SECRET` largo y aleatorio (`openssl rand -base64 48`).
- [ ] `MINIO_PUBLIC_ENDPOINT` apuntando al **dominio real** de archivos.
- [ ] Certificados TLS colocados en `infra/nginx/certs/`.
- [ ] Dominios `api.tuapp.com` y `files.tuapp.com` apuntando (DNS) a la IP del server.
- [ ] `app.conf` con tu dominio real (reemplazar `tuapp.com`).
- [ ] Firewall del host: dejar entrar SOLO `80` y `443` (el resto va por la red
      interna de Docker).
- [ ] Backups del volumen `pgdata` (la base) y `miniodata` (las fotos).
- [ ] Revisar `spring.jpa.hibernate.ddl-auto`: en prod conviene `validate` o
      manejar el esquema con migraciones, no `create`/`update` automático.

---

## 9. Resumen de la decisión

| | Dev | Prod |
|--|-----|------|
| Archivo extra | `override.yml` (auto) | `prod.yml` (explícito `-f`) |
| Secretos | `.env` | `.env.production` |
| postgres al host | `5432` publicado | **cerrado** |
| minio al host | `9000/9001` publicado | **cerrado** (via nginx) |
| backend al host | `8080` publicado | **cerrado** (via nginx) |
| Entrada pública | cada servicio | solo `nginx` (80/443) |
| TLS | no | sí (en nginx) |

La misma base sirve para los dos entornos; lo único que cambia es qué se abre al
mundo. Dev prioriza comodidad; prod prioriza que la superficie de ataque sea
**únicamente el reverse proxy**.
