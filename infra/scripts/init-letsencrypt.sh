#!/usr/bin/env bash
#
# Emision INICIAL de los certificados TLS. Se corre UNA sola vez, en el
# servidor, antes del primer `up` de produccion. La renovacion posterior es
# automatica (servicio `certbot` del compose de prod).
#
# EL PROBLEMA QUE RESUELVE
#
# nginx no arranca si los archivos de certificado no existen: la directiva
# ssl_certificate apunta a un path y si falta, falla la config. Pero Let's
# Encrypt valida el dominio pidiendo un archivo por HTTP en el puerto 80... que
# tiene que servir nginx. Sin certificado no arranca nginx; sin nginx no hay
# certificado.
#
# LA SALIDA
#
# Se generan certificados autofirmados descartables solo para que nginx pueda
# levantar. Con nginx sirviendo el puerto 80, certbot valida por webroot y emite
# los certificados reales, que pisan a los truchos. Al final se recarga nginx.
#
# Uso:
#   ./infra/scripts/init-letsencrypt.sh
#
# Probar sin gastar cuota (Let's Encrypt limita a 5 fallos por hora y por
# dominio; conviene ensayar contra el entorno de staging primero):
#   LETSENCRYPT_STAGING=1 ./infra/scripts/init-letsencrypt.sh

set -euo pipefail

cd "$(dirname "$0")/../.."

if [[ ! -f .env.production ]]; then
    echo "ERROR: falta .env.production en la raiz del repo." >&2
    echo "Copialo de .env.production.example y completalo." >&2
    exit 1
fi

set -a
# shellcheck disable=SC1091
. ./.env.production
set +a

: "${APP_DOMAIN:?falta APP_DOMAIN en .env.production (ej: miempresa.com)}"
: "${LETSENCRYPT_EMAIL:?falta LETSENCRYPT_EMAIL en .env.production (avisos de vencimiento)}"

DOMAINS=("api.${APP_DOMAIN}" "files.${APP_DOMAIN}")

COMPOSE=(docker compose --env-file .env.production
         -f docker-compose.yml -f docker-compose.prod.yml)

STAGING_ARG=""
if [[ "${LETSENCRYPT_STAGING:-0}" != "0" ]]; then
    STAGING_ARG="--staging"
    echo ">> MODO STAGING: los certificados emitidos NO son validos para navegadores."
fi

echo ">> Dominios a certificar: ${DOMAINS[*]}"
echo ">> Verifica que el DNS de ambos ya apunte a la IP de este servidor."
echo ">> Si el DNS todavia no propago, la validacion va a fallar."
read -r -p ">> Continuar? [s/N] " respuesta
[[ "$respuesta" =~ ^[sSyY]$ ]] || { echo "Cancelado."; exit 1; }

# --- 1. Certificados truchos, solo para que nginx pueda arrancar -------------
echo
echo ">> [1/4] Generando certificados temporales autofirmados..."
for dominio in "${DOMAINS[@]}"; do
    "${COMPOSE[@]}" run --rm --entrypoint sh certbot -c "
        mkdir -p /etc/letsencrypt/live/${dominio} &&
        openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
            -keyout /etc/letsencrypt/live/${dominio}/privkey.pem \
            -out   /etc/letsencrypt/live/${dominio}/fullchain.pem \
            -subj '/CN=localhost'"
done

# --- 2. nginx arriba, sirviendo el puerto 80 para la validacion --------------
echo
echo ">> [2/4] Levantando nginx..."
# --force-recreate limpia cualquier backoff de reinicio de un intento anterior:
# si nginx venia crasheando, `up -d` a secas lo deja en el ciclo de espera de
# Docker y puede no estar escuchando cuando certbot pida la validacion.
"${COMPOSE[@]}" up -d --force-recreate nginx

# Esperar a que REALMENTE sirva el puerto 80. Sin esto, certbot puede pedir la
# validacion mientras nginx todavia arranca y Let's Encrypt recibe un
# "connection refused", gastando cuota por un problema de tiempos.
echo -n ">> Esperando a que nginx responda en el puerto 80"
for _ in $(seq 1 30); do
    if curl -sf -o /dev/null "http://localhost/.well-known/acme-challenge/" \
       || curl -s -o /dev/null -w '%{http_code}' "http://localhost/" | grep -qE '^(200|301|404)$'; then
        echo " OK"
        break
    fi
    echo -n "."
    sleep 2
done

# Prueba de extremo a extremo ANTES de gastar cuota: se escribe un archivo en el
# webroot y se verifica que Let's Encrypt podria descargarlo por HTTP. Si esto
# falla, el problema esta en nginx o en el firewall, no en certbot.
echo ">> Verificando que el webroot se sirva correctamente..."
TOKEN_PRUEBA="prueba-$(date +%s)"
"${COMPOSE[@]}" run --rm --entrypoint sh certbot -c \
    "mkdir -p /var/www/certbot/.well-known/acme-challenge &&
     echo '${TOKEN_PRUEBA}' > /var/www/certbot/.well-known/acme-challenge/${TOKEN_PRUEBA}"

for dominio in "${DOMAINS[@]}"; do
    respuesta="$(curl -s --max-time 10 "http://${dominio}/.well-known/acme-challenge/${TOKEN_PRUEBA}" || true)"
    if [[ "$respuesta" != "$TOKEN_PRUEBA" ]]; then
        echo >&2
        echo "ABORTADO: ${dominio} no sirve el webroot de validacion." >&2
        echo "Se esperaba '${TOKEN_PRUEBA}' y se recibio: '${respuesta:-(nada)}'" >&2
        echo >&2
        echo "Revisa, en este orden:" >&2
        echo "  1. Que el DNS de ${dominio} apunte a este servidor: dig +short ${dominio}" >&2
        echo "  2. Que el puerto 80 este abierto en el firewall del proveedor" >&2
        echo "  3. Que nginx haya generado app.conf:" >&2
        echo "     docker compose ... exec nginx ls /etc/nginx/conf.d/" >&2
        echo "  4. Los logs: docker compose ... logs nginx" >&2
        echo >&2
        echo "No se pidio ningun certificado, asi que no se gasto cuota." >&2
        exit 1
    fi
    echo "    ${dominio}: OK"
done
"${COMPOSE[@]}" run --rm --entrypoint sh certbot -c \
    "rm -f /var/www/certbot/.well-known/acme-challenge/${TOKEN_PRUEBA}"

# --- 3. Certificados reales --------------------------------------------------
echo
echo ">> [3/4] Solicitando certificados a Let's Encrypt..."
for dominio in "${DOMAINS[@]}"; do
    # El trucho se borra recien ahora: nginx ya lo tiene cargado en memoria y
    # sigue sirviendo el puerto 80 sin problema mientras certbot valida.
    "${COMPOSE[@]}" run --rm --entrypoint sh certbot -c "
        rm -rf /etc/letsencrypt/live/${dominio} \
               /etc/letsencrypt/archive/${dominio} \
               /etc/letsencrypt/renewal/${dominio}.conf"

    "${COMPOSE[@]}" run --rm --entrypoint certbot certbot \
        certonly --webroot -w /var/www/certbot \
        ${STAGING_ARG} \
        --email "${LETSENCRYPT_EMAIL}" \
        --agree-tos --no-eff-email --non-interactive \
        -d "${dominio}"
done

# --- 4. nginx toma los certificados reales -----------------------------------
echo
echo ">> [4/4] Recargando nginx..."
"${COMPOSE[@]}" exec nginx nginx -s reload

echo
echo ">> Listo. Certificados emitidos para: ${DOMAINS[*]}"
echo ">> Ahora levantas el stack completo:"
echo "     docker compose --env-file .env.production \\"
echo "       -f docker-compose.yml -f docker-compose.prod.yml up -d --build"
