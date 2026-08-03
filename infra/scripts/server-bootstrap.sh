#!/usr/bin/env bash
#
# Preparacion inicial del servidor (Ubuntu limpio). Se corre UNA vez, como root,
# en el VPS recien creado.
#
# Que hace:
#   1. Actualiza el sistema y activa parches de seguridad automaticos
#   2. Crea un usuario no-root con sudo y tu clave SSH
#   3. Firewall: solo 22, 80 y 443
#   4. Swap de 2GB (colchon para el build de Maven)
#   5. Docker CE + Compose desde el repositorio oficial
#
# Que NO hace, a proposito: NO desactiva el login por contraseña ni el acceso
# root. Eso va en server-harden-ssh.sh, que se corre DESPUES de verificar que
# podes entrar con la clave. Hacerlo todo junto es la forma mas comun de
# quedarse afuera del propio servidor.
#
# Uso, desde TU maquina (con el repo clonado):
#   scp -P PUERTO infra/scripts/server-bootstrap.sh root@IP_DEL_VPS:/root/
#   ssh -p PUERTO root@IP_DEL_VPS 'bash /root/server-bootstrap.sh enzo "$(cat ~/.ssh/id_ed25519.pub)"'
#
# OJO CON EL PUERTO: varios proveedores no usan el 22. DonWeb, por ejemplo,
# asigna uno aleatorio que figura en el panel (Cloud & IaaS > Administrar >
# Software y Accesos > SSH). El script detecta solo el puerto real para el
# firewall, pero vos necesitas saberlo para conectarte.
# Ojo tambien con la mayuscula: scp usa -P y ssh usa -p.

set -euo pipefail

if [[ $EUID -ne 0 ]]; then
    echo "ERROR: corrreme como root en el servidor." >&2
    exit 1
fi

USUARIO="${1:-}"
CLAVE_PUBLICA="${2:-}"

if [[ -z "$USUARIO" || -z "$CLAVE_PUBLICA" ]]; then
    echo "Uso: bash server-bootstrap.sh <usuario> \"<clave-publica-ssh>\"" >&2
    echo "Ej:  bash server-bootstrap.sh enzo \"ssh-ed25519 AAAAC3... enzo@mac\"" >&2
    exit 1
fi

if [[ ! "$CLAVE_PUBLICA" =~ ^(ssh-ed25519|ssh-rsa|ecdsa-) ]]; then
    echo "ERROR: eso no parece una clave publica SSH." >&2
    echo "Tiene que empezar con ssh-ed25519, ssh-rsa o ecdsa-." >&2
    echo "OJO: es la clave PUBLICA (.pub), nunca la privada." >&2
    exit 1
fi

echo "==> [1/5] Actualizando el sistema..."
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get upgrade -y -qq
# Parches de seguridad automaticos: un servidor expuesto sin actualizar es
# vulnerable a exploits publicados meses atras.
apt-get install -y -qq unattended-upgrades ca-certificates curl gnupg git
dpkg-reconfigure -f noninteractive unattended-upgrades

echo "==> [2/5] Creando el usuario '${USUARIO}'..."
if id -u "$USUARIO" >/dev/null 2>&1; then
    echo "    ya existe, se reutiliza"
else
    adduser --disabled-password --gecos "" "$USUARIO"
fi
usermod -aG sudo "$USUARIO"

HOME_USUARIO="/home/${USUARIO}"
mkdir -p "${HOME_USUARIO}/.ssh"
if ! grep -qF "$CLAVE_PUBLICA" "${HOME_USUARIO}/.ssh/authorized_keys" 2>/dev/null; then
    echo "$CLAVE_PUBLICA" >> "${HOME_USUARIO}/.ssh/authorized_keys"
fi
chmod 700 "${HOME_USUARIO}/.ssh"
chmod 600 "${HOME_USUARIO}/.ssh/authorized_keys"
chown -R "${USUARIO}:${USUARIO}" "${HOME_USUARIO}/.ssh"

echo "==> [3/5] Configurando el firewall..."
apt-get install -y -qq ufw

# NO se asume el puerto 22. Varios proveedores (DonWeb entre ellos) mueven SSH a
# un puerto aleatorio por seguridad. Abrir el 22 a ciegas y activar ufw abriria
# un puerto que no se usa mientras cierra el que si, expulsandote del servidor
# en el acto. Se lee el puerto real de la config efectiva de sshd.
PUERTOS_SSH="$(sshd -T 2>/dev/null | awk '/^port /{print $2}')"
if [[ -z "$PUERTOS_SSH" ]]; then
    echo "ERROR: no pude detectar en que puerto escucha SSH." >&2
    echo "Sin ese dato, activar el firewall te dejaria afuera. Abortando." >&2
    echo "Revisalo con: sshd -T | grep '^port'" >&2
    exit 1
fi
echo "    SSH detectado en el/los puerto(s): ${PUERTOS_SSH}"

# El orden importa: primero se permite, DESPUES se activa.
for puerto in $PUERTOS_SSH; do
    ufw allow "${puerto}/tcp" comment 'SSH'
done
ufw allow 80/tcp   comment 'HTTP (redirect + validacion ACME)'
ufw allow 443/tcp  comment 'HTTPS'
ufw --force enable
ufw status verbose

echo "==> [4/5] Creando swap de 2GB..."
# La app en marcha usa ~2.5GB, pero docker-compose construye el backend EN EL
# SERVIDOR con Maven, y ese build es el pico de memoria de todo el sistema. El
# swap evita que muera por OOM justo durante un despliegue.
if [[ -f /swapfile ]]; then
    echo "    /swapfile ya existe, se omite"
else
    fallocate -l 2G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
    # Preferir RAM sobre swap: el swap es red de contencion, no memoria de uso
    # diario (en disco es ordenes de magnitud mas lento).
    sysctl -w vm.swappiness=10
    echo 'vm.swappiness=10' > /etc/sysctl.d/99-swappiness.conf
fi
free -h

echo "==> [5/5] Instalando Docker CE y Compose..."
if command -v docker >/dev/null 2>&1; then
    echo "    docker ya instalado, se omite"
else
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc
    # shellcheck disable=SC1091  # /etc/os-release solo existe en el servidor
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
        > /etc/apt/sources.list.d/docker.list
    apt-get update -qq
    apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
# Permite usar docker sin sudo. Equivale a dar acceso root, asi que solo va para
# el usuario de despliegue.
usermod -aG docker "$USUARIO"
systemctl enable --now docker
docker --version
docker compose version

cat <<FIN

========================================================================
Servidor preparado.

PROXIMO PASO — verifica el acceso ANTES de endurecer SSH. Desde TU maquina,
en una terminal NUEVA (no cierres esta):

    ssh ${USUARIO}@$(curl -s -4 ifconfig.me 2>/dev/null || echo "IP_DEL_VPS")

Si entra sin pedirte contraseña, recien ahi:

    scp infra/scripts/server-harden-ssh.sh ${USUARIO}@IP:/tmp/
    ssh ${USUARIO}@IP 'sudo bash /tmp/server-harden-ssh.sh'

Si NO entra, no corras el harden: revisa la clave publica y volve a
intentar. Mientras no lo corras, seguis teniendo el acceso por contraseña
como red de seguridad.
========================================================================
FIN
