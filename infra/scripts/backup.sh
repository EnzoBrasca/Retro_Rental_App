#!/usr/bin/env bash
#
# Backup de produccion: la base Postgres + las fotos de MinIO.
#
# Son las DOS cosas irrecuperables del sistema. Todo lo demas (imagenes,
# configuracion, codigo) se reconstruye desde el repo; los tickets cargados por
# los empleados y sus fotos, no.
#
# Uso manual:
#   ./infra/scripts/backup.sh
#
# Automatico (cron del servidor, todos los dias a las 3 AM):
#   0 3 * * * cd /ruta/al/repo && ./infra/scripts/backup.sh >> /var/log/retrorental-backup.log 2>&1
#
# Los backups quedan en el disco local Y se copian a un bucket de Cloudflare
# R2 via rclone (remote "r2", configurado con `rclone config`). La retencion
# en R2 la maneja una lifecycle rule del bucket, no este script.

set -euo pipefail

cd "$(dirname "$0")/../.."

DESTINO="${BACKUP_DIR:-./backups}"
RETENCION_DIAS="${BACKUP_RETENTION_DAYS:-14}"
SELLO="$(date +%Y%m%d-%H%M%S)"

if [[ ! -f .env.production ]]; then
    echo "ERROR: falta .env.production en la raiz del repo." >&2
    exit 1
fi

set -a
# shellcheck disable=SC1091
. ./.env.production
set +a

: "${DB_USER:?falta DB_USER en .env.production}"
: "${DB_NAME:?falta DB_NAME en .env.production}"

COMPOSE=(docker compose --env-file .env.production
         -f docker-compose.yml -f docker-compose.prod.yml)

mkdir -p "${DESTINO}"

# --- Postgres ----------------------------------------------------------------
# Formato custom (-Fc): comprimido y restaurable con pg_restore de forma
# selectiva, a diferencia del SQL plano.
echo ">> Backup de Postgres..."
"${COMPOSE[@]}" exec -T postgres \
    pg_dump -U "${DB_USER}" -d "${DB_NAME}" -Fc \
    > "${DESTINO}/db-${SELLO}.dump"

# --- MinIO -------------------------------------------------------------------
# --volumes-from toma el volumen del contenedor de MinIO sin tener que adivinar
# su nombre (que depende del nombre del proyecto de Compose).
echo ">> Backup de las fotos de MinIO..."
MINIO_CID="$("${COMPOSE[@]}" ps -q minio)"
if [[ -z "${MINIO_CID}" ]]; then
    echo "ERROR: el contenedor de minio no esta corriendo." >&2
    exit 1
fi
docker run --rm \
    --volumes-from "${MINIO_CID}" \
    -v "$(cd "${DESTINO}" && pwd):/backup" \
    alpine tar czf "/backup/minio-${SELLO}.tar.gz" -C /data .

# --- Retencion local -----------------------------------------------------
echo ">> Borrando backups de mas de ${RETENCION_DIAS} dias..."
find "${DESTINO}" -name 'db-*.dump'      -mtime "+${RETENCION_DIAS}" -delete
find "${DESTINO}" -name 'minio-*.tar.gz' -mtime "+${RETENCION_DIAS}" -delete

# --- Copia fuera del servidor (Cloudflare R2) ---------------------------------
# La retencion en R2 la maneja una lifecycle rule del bucket, no este script.
R2_REMOTE="${R2_REMOTE:-r2:retrorental-backups}"
if command -v rclone >/dev/null 2>&1; then
    echo ">> Subiendo a ${R2_REMOTE}..."
    rclone copy "${DESTINO}/db-${SELLO}.dump"    "${R2_REMOTE}"
    rclone copy "${DESTINO}/minio-${SELLO}.tar.gz" "${R2_REMOTE}"
else
    echo "ADVERTENCIA: rclone no esta instalado, se salteo la copia a R2." >&2
fi

echo ">> Listo:"
ls -lh "${DESTINO}/db-${SELLO}.dump" "${DESTINO}/minio-${SELLO}.tar.gz"
