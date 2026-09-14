# RetroRental

App de gestión de flota y carga de combustible. Monorepo con tres partes de stack bien distinto — no asumas un framework sin mirar primero en qué carpeta estás.

## Stack por carpeta

- `backend/` — Java, Spring Boot 3, JPA/Hibernate, Postgres, migraciones Flyway.
- `mobile/` — Expo / React Native. Ver `mobile/AGENTS.md`: Expo cambió mucho, no asumir APIs de memoria.
- `infra/` — Docker Compose (dev/prod separados), nginx como reverse proxy, scripts de backup y TLS.

## Skills de `fullstack-dev-skills` relevantes a este repo

El plugin trae ~80 skills genéricas por stack. La mayoría (Django, Rails, Kubernetes, Flutter...) no aplica acá. Antes de tocar código, cargar la que corresponda a la carpeta:

| Carpeta / tarea | Skill |
| --- | --- |
| `backend/` (Spring Boot, JPA) | `fullstack-dev-skills:java-architect` |
| Migraciones, queries, índices de Postgres | `fullstack-dev-skills:postgres-pro` |
| `mobile/` (Expo, React Native) | `fullstack-dev-skills:react-native-expert` |
| `infra/` (Docker, nginx, backups, R2/S3) | `fullstack-dev-skills:devops-engineer` |
| Auth, validación de input, hashing, JWT, headers CORS/CSP | `fullstack-dev-skills:secure-code-guardian` |
| Auditoría de vulnerabilidades con reporte de severidad | `fullstack-dev-skills:security-reviewer` (si el pedido es "revisar seguridad" en general, preferir la skill nativa `security-review`; usar esta cuando el pedido pide explícitamente auditoría/compliance/SAST) |
| Feature nueva que cruza frontend + backend + seguridad en un mismo flujo | `fullstack-dev-skills:fullstack-guardian` |
| Decisiones de arquitectura, ADRs, evaluación de trade-offs | `fullstack-dev-skills:architecture-designer` |
| Documentación de API (OpenAPI/Swagger), docstrings | `fullstack-dev-skills:code-documenter` |

Las demás skills del paquete se ignoran para este proyecto salvo pedido explícito.
