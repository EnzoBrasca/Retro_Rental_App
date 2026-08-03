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
# IMPORTANTE: esto deja los backups en el MISMO servidor. Eso te cubre de un
# borrado accidental o de una migracion mal hecha, pero NO de que se muera el
# server. Copialos a otro lado (rclone a un bucket, scp a otra maquina) o el dia
# que se caiga el disco perdes los backups junto con los datos.

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

# --- Retencion ---------------------------------------------------------------
echo ">> Borrando backups de mas de ${RETENCION_DIAS} dias..."
find "${DESTINO}" -name 'db-*.dump'      -mtime "+${RETENCION_DIAS}" -delete
find "${DESTINO}" -name 'minio-*.tar.gz' -mtime "+${RETENCION_DIAS}" -delete

echo ">> Listo:"
ls -lh "${DESTINO}/db-${SELLO}.dump" "${DESTINO}/minio-${SELLO}.tar.gz"
