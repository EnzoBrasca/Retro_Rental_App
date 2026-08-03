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
| `docker-compose.prod.yml` | **Prod**: reverse proxy, certbot, `restart`, `.env.production`, cero puertos directos. | Explícito con `-f`. |
| `infra/nginx/templates/app.conf.template` | Config del reverse proxy (rutas por subdominio + TLS). Es una **plantilla**: el dominio sale de `APP_DOMAIN`. | Solo en prod. |

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

# 2. Emitir los certificados TLS (UNA sola vez, ver sección 6)
./infra/scripts/init-letsencrypt.sh

# 3. Levantar el stack de producción
docker compose --env-file .env.production \
  -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

Al pasar los `-f` explícitos, Compose **ignora** el `override.yml` de dev y usa
el `prod.yml` en su lugar.

El paso 2 va **antes** del 3 y no es opcional: nginx no arranca sin los archivos
de certificado, así que sin ese paso el stack no levanta.

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
   adentro; el cifrado vive en el borde. El mobile le pega a `https://api.miempresa.com`.
2. **Un solo punto de entrada**: toda la superficie pública es el proxy. Más fácil
   de asegurar, monitorear y ponerle rate-limiting.
3. **Dominio en vez de IP:puerto**: el mobile apunta a un dominio estable
   (`api.miempresa.com`), no a `123.45.67.89:8080`. Podés mover el server o escalar sin
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
| Prod    | `https://files.miempresa.com` | `.env.production` |

**Si en producción dejás `localhost`, el mobile nunca va a poder descargar una
foto**: la URL presignada apuntaría a la máquina del celular, no al server. Por eso
en prod se rutea un subdominio (`files.miempresa.com`) hacia `minio:9000` vía nginx, y
ese es el valor de `MINIO_PUBLIC_ENDPOINT`.

nginx reenvía el `Host` original a MinIO para que la firma de la URL valide bien
(ver `infra/nginx/templates/app.conf.template`).

---

## 6. Certificados TLS (Let's Encrypt, automático)

Los certificados se emiten y renuevan solos. Viven en el volumen `letsencrypt`,
no en el repo.

### El problema del arranque

nginx **no arranca si el archivo de certificado no existe**: la directiva
`ssl_certificate` apunta a un path y si falta, la config es inválida. Pero Let's
Encrypt valida el dominio pidiendo un archivo por HTTP plano en el puerto 80, que
tiene que servir nginx. Sin certificado no hay nginx; sin nginx no hay
certificado.

`infra/scripts/init-letsencrypt.sh` rompe ese círculo en cuatro pasos:

1. Genera certificados **autofirmados descartables**, solo para que nginx pueda
   levantar.
2. Levanta nginx, que ya sirve el puerto 80.
3. Borra los truchos y pide los reales a Let's Encrypt vía webroot.
4. Recarga nginx, que toma los certificados buenos.

```bash
./infra/scripts/init-letsencrypt.sh
```

Antes de correrlo, el DNS de `api.` y `files.` ya tiene que apuntar a la IP del
servidor: la validación consiste justamente en que Let's Encrypt resuelva esos
nombres y llegue a tu nginx.

**Ensayá primero contra staging.** Let's Encrypt limita a 5 fallos por hora y por
dominio; si te quedás sin cuota tenés que esperar:

```bash
LETSENCRYPT_STAGING=1 ./infra/scripts/init-letsencrypt.sh
```

Los certificados de staging no son válidos para navegadores, pero confirman que
todo el circuito (DNS, puerto 80, webroot) funciona. Cuando salga bien, corré el
script sin la variable.

### Renovación

El servicio `certbot` del compose de prod corre `certbot renew` cada 12 horas
(Let's Encrypt renueva recién cuando faltan menos de 30 días, así que no hay
riesgo de agotar la cuota). En paralelo, nginx se recarga cada 6 horas.

Esa recarga periódica **no es opcional**: nginx mantiene el certificado cargado
en memoria, así que aunque certbot escriba uno nuevo en disco, seguiría sirviendo
el viejo hasta reiniciar — y se vencería igual que si no hubiera renovación.

### Certificados propios de la organización

Si en vez de Let's Encrypt usás certificados de la empresa, colocalos dentro del
volumen `letsencrypt` respetando la estructura que espera la plantilla:

```
/etc/letsencrypt/live/api.TUDOMINIO/{fullchain,privkey}.pem
/etc/letsencrypt/live/files.TUDOMINIO/{fullchain,privkey}.pem
```

y no corras `init-letsencrypt.sh` ni el servicio `certbot`.

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
| `SPRING_PROFILES_ACTIVE` | `prod` | **Crítico.** Ver abajo. |

Si las definieras en el `.env` con otro valor (ej. `DB_HOST=localhost`), romperías
el routing interno. Por eso el compose las impone en el bloque `environment`.

### Por qué `SPRING_PROFILES_ACTIVE` lo impone el compose

`application.yml` define `${SPRING_PROFILES_ACTIVE:dev}`: si nadie la setea, el
default es **dev**. Olvidarla en producción no da ningún error — la app arranca
normal — pero con el perfil dev activo corre `DataSeeder` (`@Profile("dev")`), que
sembraría **proveedores y precios de prueba en la base del cliente**, además de
dejar el logging en DEBUG.

Es una falla silenciosa y grave, así que no se confía a un `.env` que alguien
puede olvidar completar: la fija `docker-compose.prod.yml`.

### Variables nuevas de producción

| Variable | Ejemplo | Para qué |
|----------|---------|----------|
| `APP_DOMAIN` | `miempresa.com` | Dominio base. La plantilla de nginx arma `api.` y `files.` a partir de acá. |
| `LETSENCRYPT_EMAIL` | `soporte@miempresa.com` | Avisos de vencimiento de certificados. |
| `MINIO_PUBLIC_ENDPOINT` | `https://files.miempresa.com` | Host con el que se firman las URLs presignadas (ver sección 5). |

---

## 8. Migraciones de base de datos (Flyway)

El schema **no lo maneja Hibernate**. En todos los perfiles `ddl-auto` es
`validate`: Hibernate solo verifica que las entidades coincidan con las tablas, y
si no coinciden la app **no arranca**. Quien crea y modifica el schema es Flyway,
con los archivos de `backend/src/main/resources/db/migration/`.

```
db/migration/
└── V1__init_schema.sql    # schema completo inicial
```

### Cómo agregar un cambio de schema

1. Creá un archivo nuevo: `V2__descripcion_corta.sql`. **Nunca edites un archivo
   ya aplicado** — Flyway guarda un checksum de cada uno en la tabla
   `flyway_schema_history` y falla el arranque si detecta que cambió.
2. Escribí el `ALTER`/`CREATE` correspondiente.
3. Actualizá la entidad JPA.
4. Levantá la app: Flyway aplica la migración y `validate` confirma que entidad y
   tabla quedaron alineadas.

Si te olvidás del paso 1 y solo tocás la entidad, la app no arranca y te enterás
en tu máquina — que es exactamente el punto. Antes, con `ddl-auto: update`,
Hibernate parchaba la DB en silencio y dev y prod divergían sin que nadie lo viera.

### Primer despliegue

No hay que hacer nada especial: la DB de producción arranca vacía y Flyway la
construye desde `V1`. `baseline-on-migrate` está en `false` a propósito — si
alguna vez hay que adoptar una base preexistente, se activa a conciencia y no por
accidente.

### El catálogo de producción NO se siembra solo

`DataSeeder` es `@Profile("dev")`: siembra proveedores y precios de prueba solo en
desarrollo. **Producción arranca con `proveedores` y `precios` vacíos**, y sin
catálogo el formulario de carga de tickets no puede resolver el precio — el flujo
principal de la app queda inutilizable.

Hoy no existe ABM de proveedores ni de precios (`ProveedorController` y
`PrecioController` son solo lectura), así que la carga inicial es SQL directo
contra la base:

```bash
docker compose --env-file .env.production \
  -f docker-compose.yml -f docker-compose.prod.yml \
  exec postgres psql -U "$DB_USER" -d "$DB_NAME"
```

Insertá los proveedores reales de la empresa y un precio vigente
(`fecha_hasta IS NULL`) por cada par (proveedor, combustible) que se vaya a usar.

### Crear el primer administrador

`POST /auth/register` es público y **siempre crea un EMPLEADO**. El rol no se
acepta desde el body: si se aceptara, cualquiera con el APK se daría permisos de
administrador con dos toques.

Para tener el primer admin: registrate normalmente desde la app y después promové
esa cuenta en la base.

**Cuidado: no alcanza con cambiar `personas.rol`.** El modelo usa herencia JOINED
(`@Inheritance(InheritanceType.JOINED)`), y ahí el subtipo de la entidad lo
determina **en qué tabla hija vive la fila**, no la columna `rol`:

| Qué controla | Quién lo determina |
|---|---|
| Autorización HTTP (`/admin/**`) | `personas.rol` → claim del JWT → `ROLE_*` |
| Subtipo JPA (Empleado / Administrador) | fila en `empleados` o en `administradores` |

Si solo actualizás `rol`, la cuenta entra a `/admin/**` pero para el ORM sigue
siendo un `Empleado`. Consecuencias reales:

- No se le puede asignar como jefe de nadie: `empleados.id_administrador`
  referencia `administradores(id)`, y esa fila no existe → violación de FK.
- Mismo problema con `administrador_vehiculo.id_administrador`.
- Sigue apareciendo en el listado de empleados del ABM, con su `fecha_alta`.

La promoción correcta **mueve la fila** entre tablas hijas, en una transacción:

```sql
BEGIN;

-- 1. El rol, que es lo que habilita /admin/**
UPDATE personas SET rol = 'ADMINISTRADOR' WHERE username = 'juanperez';

-- 2. La fila en la tabla hija correcta
INSERT INTO administradores (id) SELECT id FROM personas WHERE username = 'juanperez';
DELETE FROM empleados WHERE id = (SELECT id FROM personas WHERE username = 'juanperez');

COMMIT;
```

Hacelo con una cuenta **recién registrada**: si esa persona ya cargó tickets o
figura como operario de algún vehículo, el `DELETE FROM empleados` falla por las
FKs que la referencian, y ahí conviene crear un usuario nuevo para el admin en
lugar de promover uno con historial.

---

## 9. Backups

`infra/scripts/backup.sh` respalda las **dos cosas irrecuperables**: la base
Postgres y las fotos de MinIO. Todo lo demás (imágenes, config, código) se
reconstruye desde el repo; los tickets que cargaron los empleados, no.

```bash
./infra/scripts/backup.sh
```

Automatizalo con cron en el servidor:

```cron
0 3 * * * cd /ruta/al/repo && ./infra/scripts/backup.sh >> /var/log/retrorental-backup.log 2>&1
```

Variables opcionales: `BACKUP_DIR` (default `./backups`) y
`BACKUP_RETENTION_DAYS` (default 14).

**Un backup en el mismo servidor no es un backup.** Te cubre de un borrado
accidental o de una migración mal hecha, pero no de que se muera el disco. Copialos
afuera (`rclone` a un bucket, `scp` a otra máquina) o el día que se caiga el server
perdés los datos y los backups juntos.

Para restaurar la base: `pg_restore -U $DB_USER -d $DB_NAME --clean db-FECHA.dump`.

---

## 10. Checklist de producción

Antes de exponer el server:

- [ ] `.env.production` creado con **passwords fuertes** (DB, MinIO) y un
      `JWT_SECRET` largo y aleatorio (`openssl rand -base64 48`).
- [ ] `APP_DOMAIN` y `LETSENCRYPT_EMAIL` completados.
- [ ] `MINIO_PUBLIC_ENDPOINT` apuntando al **dominio real** de archivos.
- [ ] Dominios `api.APP_DOMAIN` y `files.APP_DOMAIN` apuntando (DNS) a la IP del server.
- [ ] `init-letsencrypt.sh` corrido (probá primero con `LETSENCRYPT_STAGING=1`).
- [ ] Firewall del host: dejar entrar SOLO `22`, `80` y `443` (el resto va por la
      red interna de Docker).
- [ ] Cron de `backup.sh` configurado **y** copia de los backups fuera del server.
- [ ] Catálogo de proveedores y precios cargado (sección 8) — sin esto el
      formulario de carga de tickets no funciona.
- [ ] Primer administrador creado y promovido (sección 8).
- [ ] Smoke test contra el dominio real: registro → login → carga de ticket con
      foto → ver la foto en el historial.
- [ ] Mobile compilado apuntando a `https://api.APP_DOMAIN`.

---

## 11. Resumen de la decisión

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
