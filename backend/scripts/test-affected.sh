#!/usr/bin/env bash
# Corre solo los tests del backend cuyo dominio fue tocado por el diff local,
# usando los @Tag de JUnit 5 (ver domain-tags.conf para el mapeo).
#
# Uso: backend/scripts/test-affected.sh [ref-base]
#   ref-base: rama/commit contra el que se compara (default: main)
#
# Si algun archivo tocado no matchea ningun dominio conocido, o no hay forma
# de determinar el diff, cae al fallback seguro: corre la suite completa.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${BACKEND_DIR}/.." && pwd)"
CONF="${SCRIPT_DIR}/domain-tags.conf"
BASE_REF="${1:-main}"

run_full_suite() {
    echo "==> Corriendo la suite completa (mvn test, sin filtro de tags)."
    cd "${BACKEND_DIR}"
    exec ./mvnw test
}

cd "${REPO_ROOT}"

if ! git rev-parse --verify --quiet "${BASE_REF}" > /dev/null; then
    echo "No se pudo resolver la referencia base '${BASE_REF}'."
    run_full_suite
fi

CHANGED="$(git diff --name-only "${BASE_REF}" -- backend/src/main/java || true)"

if [ -z "${CHANGED}" ]; then
    echo "No hay cambios en backend/src/main/java respecto a ${BASE_REF}. Nada que correr."
    exit 0
fi

echo "Archivos main tocados respecto a ${BASE_REF}:"
echo "${CHANGED}" | sed 's/^/  /'

TAGS_FOUND=""
UNMATCHED=0

while IFS= read -r file; do
    [ -z "${file}" ] && continue
    matched=0
    while IFS='=' read -r pattern tag; do
        [[ -z "${pattern}" || "${pattern}" == \#* ]] && continue
        if [[ "${file}" =~ ${pattern} ]]; then
            TAGS_FOUND="${TAGS_FOUND} ${tag}"
            matched=1
        fi
    done < "${CONF}"
    if [ "${matched}" -eq 0 ]; then
        echo "  sin dominio mapeado: ${file}"
        UNMATCHED=1
    fi
done <<< "${CHANGED}"

if [ "${UNMATCHED}" -eq 1 ]; then
    echo "Hay archivos sin dominio mapeado en domain-tags.conf: no se puede garantizar"
    echo "que la seleccion por tags cubra el impacto real."
    run_full_suite
fi

UNIQUE_TAGS="$(printf '%s\n' core ${TAGS_FOUND} | sort -u | paste -sd, -)"
echo "==> Dominios afectados: ${UNIQUE_TAGS}"

cd "${BACKEND_DIR}"
exec ./mvnw test "-Dgroups=${UNIQUE_TAGS}"
