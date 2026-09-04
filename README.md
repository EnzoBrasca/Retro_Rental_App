# RetroRental

Sistema de gestión de flota vehicular y carga de combustible para una empresa con vehículos y maquinaria vial. Nace de un problema concreto: la carga de combustible se registraba en papel o en planillas sueltas, sin forma de cruzar consumo contra kilometraje/horas de uso ni de tener un historial confiable por vehículo. La app digitaliza ese flujo desde el celular del operario.

Es un monorepo con tres partes de stack bien distinto, pensadas para desplegarse juntas pero desarrollarse por separado.

## Estructura del repo

```
backend/    API REST en Spring Boot (Java 21)
mobile/     App para Android/iOS en Expo / React Native
infra/      Docker Compose, nginx como reverse proxy, backups y TLS
docs/       Documentación técnica, auditorías y políticas
```

## Stack

| Capa | Tecnología |
| --- | --- |
| Backend | Java 21, Spring Boot 4, Spring Security, Spring Data JPA / Hibernate |
| Base de datos | PostgreSQL 16, migraciones con Flyway |
| Almacenamiento de archivos | MinIO (S3-compatible), fotos de tickets |
| Auth | JWT (jjwt), roles EMPLEADO / ADMINISTRADOR |
| OCR | API de Mistral para leer los datos del ticket desde la foto |
| Mobile | Expo SDK 54, React Native 0.81, React 19, expo-router |
| Infra | Docker Compose (dev y prod separados), nginx, Let's Encrypt, rclone hacia Cloudflare R2 para backups |
| CI | GitHub Actions (`mobile.yml`) |
| Cobertura de tests | JaCoCo (backend) |
| Análisis de dependencias | OWASP dependency-check, perfil opt-in |

## Funcionalidades principales

- **Registro de cargas de combustible**: el empleado saca una foto del ticket y del tablero/odómetro, la app resuelve el proveedor y el precio vigente contra el catálogo, y el OCR (Mistral) pre-completa litros, fecha, importe y estación para no tener que tipear todo a mano.
- **Gestión de flota**: alta de vehículos y herramientas (motosierras, bidones, etc.), seguimiento de uso acumulado (kilómetros u horas según corresponda) y mantenimiento.
- **Catálogo de proveedores y precios**: ABM de proveedores, con soporte para un proveedor genérico ("Otros") para estaciones no habituales, y precios vigentes por combustible que se actualizan por historial, nunca se pisan.
- **Roles diferenciados**: el registro público (`POST /auth/register`) siempre crea un EMPLEADO; los ADMINISTRADOR se dan de alta por fuera de ese endpoint, a propósito, para que nadie se autoasigne permisos. El modelo usa herencia JOINED (JPA), así que promover a un usuario implica mover la fila entre tablas, no solo cambiar un campo de rol.
- **Padrón de habilitados**: el registro self-service de empleados solo funciona si su documento fue cargado antes por un administrador, para que la app no quede abierta a cualquiera con el link.
- **Estadísticas**: consumo por vehículo, por proveedor, por período, expuesto vía `StatsController`.

## Cómo levantar el entorno de desarrollo

Requiere Docker y Docker Compose.

```bash
cp .env.example .env
docker compose up --build
```

Esto levanta backend, Postgres y MinIO. El override de desarrollo (`docker-compose.override.yml`) se mergea automáticamente.

Para el mobile:

```bash
cd mobile
npm install
npm start
```

## Testing

Backend:

```bash
cd backend
./mvnw test
```

El test de contexto de Spring levanta Postgres real en un contenedor (Testcontainers) y corre las migraciones de Flyway de verdad contra ese schema, así que valida que las entidades JPA coincidan con la base — no solo que el código compile. Requiere Docker corriendo.

Mobile:

```bash
cd mobile
npm run verify   # typecheck + lint + format:check + test
```

## Deploy

El procedimiento completo (bootstrap del servidor, hardening de SSH, TLS con Let's Encrypt, alta del primer administrador, backups) está documentado en [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md).

Los backups de Postgres y de las fotos en MinIO se replican a Cloudflare R2 vía `rclone`, además de quedar en el disco del VPS — un backup que vive en el mismo servidor que respalda no es un backup real.

## Documentación adicional

- [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) — puesta en producción
- [`docs/AUTH.md`](docs/AUTH.md) — modelo de autenticación y roles
- [`docs/OCR.md`](docs/OCR.md) — integración de OCR con Mistral
- [`docs/STORAGE.md`](docs/STORAGE.md) — almacenamiento de archivos (MinIO)
- [`docs/SECURITY-AUDIT.md`](docs/SECURITY-AUDIT.md) — auditoría de seguridad
- [`docs/BACKEND-AUDIT.md`](docs/BACKEND-AUDIT.md) / [`docs/BACKEND-PRINCIPLES-AUDIT.md`](docs/BACKEND-PRINCIPLES-AUDIT.md) — auditorías del backend (performance/queries y SOLID/DRY/YAGNI respectivamente)
- [`docs/FRONTEND-AUDIT.md`](docs/FRONTEND-AUDIT.md) / [`docs/MOBILE-PRINCIPLES-AUDIT.md`](docs/MOBILE-PRINCIPLES-AUDIT.md) — auditorías del mobile
- [`docs/ERRORS.md`](docs/ERRORS.md) — catálogo de errores de la API
