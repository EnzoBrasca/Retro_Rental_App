#!/usr/bin/env bash
#
# Endurecimiento de SSH. Se corre DESPUES de server-bootstrap.sh y SOLO cuando
# ya verificaste que podes entrar con tu clave.
#
# Que hace:
#   - Desactiva el login por contraseña (solo clave publica)
#   - Desactiva el acceso directo como root
#
# POR QUE VA SEPARADO: mientras el login por contraseña siga activo, tenes una
# via de entrada de respaldo si algo sale mal con la clave. Este script quema
# esa red. Si lo corres sin haber verificado el acceso por clave y algo esta
# mal configurado, te quedas afuera del servidor y hay que recuperarlo desde la
# consola del panel de DonWeb.
#
# Uso, desde TU maquina:
#   scp infra/scripts/server-harden-ssh.sh USUARIO@IP:/tmp/
#   ssh USUARIO@IP 'sudo bash /tmp/server-harden-ssh.sh'

set -euo pipefail

if [[ $EUID -ne 0 ]]; then
    echo "ERROR: necesito root. Corremelo con: sudo bash $0" >&2
    exit 1
fi

# --- Verificacion de seguridad ------------------------------------------------
# Antes de desactivar las contraseñas, comprobamos que exista al menos un
# usuario no-root con clave publica cargada. Sin esto, el script te podria
# dejar un servidor al que nadie puede entrar.
echo "==> Verificando que haya acceso por clave configurado..."
USUARIOS_CON_CLAVE=()
for dir in /home/*; do
    [[ -d "$dir" ]] || continue
    archivo="${dir}/.ssh/authorized_keys"
    if [[ -s "$archivo" ]]; then
        USUARIOS_CON_CLAVE+=("$(basename "$dir")")
    fi
done

if [[ ${#USUARIOS_CON_CLAVE[@]} -eq 0 ]]; then
    echo >&2
    echo "ABORTADO: ningun usuario tiene authorized_keys cargado." >&2
    echo "Si desactivara las contraseñas ahora, nadie podria entrar." >&2
    echo "Configura tu clave publica primero (ver server-bootstrap.sh)." >&2
    exit 1
fi
echo "    usuarios con clave: ${USUARIOS_CON_CLAVE[*]}"

echo
echo "Estas por DESACTIVAR el login por contraseña y el acceso root."
echo "Confirma que en OTRA terminal ya entraste con: ssh ${USUARIOS_CON_CLAVE[0]}@<ip>"
read -r -p "Verificaste que entra con la clave? [s/N] " respuesta
[[ "$respuesta" =~ ^[sSyY]$ ]] || { echo "Cancelado. Nada fue modificado."; exit 1; }

# --- Configuracion ------------------------------------------------------------
# Se escribe en un drop-in de sshd_config.d en vez de editar sshd_config: es mas
# limpio, sobrevive a las actualizaciones del paquete y se revierte borrando un
# solo archivo.
echo "==> Escribiendo la configuracion..."
mkdir -p /etc/ssh/sshd_config.d
cat > /etc/ssh/sshd_config.d/99-hardening.conf <<'CONF'
# Endurecimiento aplicado por infra/scripts/server-harden-ssh.sh
# Para revertir: borrar este archivo y reiniciar ssh.

# Solo clave publica. Las contraseñas son vulnerables a fuerza bruta, y un
# servidor con IP publica recibe intentos automatizados desde el primer dia.
PasswordAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes

# Sin acceso directo como root: se entra con el usuario propio y se escala con
# sudo, que ademas deja rastro de quien hizo que.
PermitRootLogin no
CONF

echo "==> Validando la configuracion antes de aplicarla..."
# sshd -t falla si la config tiene errores. Validar ANTES de recargar evita
# dejar el servicio caido con una config invalida.
if ! sshd -t; then
    echo "ERROR: la configuracion de sshd es invalida. Revirtiendo." >&2
    rm -f /etc/ssh/sshd_config.d/99-hardening.conf
    exit 1
fi

systemctl reload ssh 2>/dev/null || systemctl reload sshd

cat <<FIN

========================================================================
SSH endurecido: solo clave publica, sin acceso root directo.

NO CIERRES ESTA SESION todavia. Abri una terminal nueva y confirma:

    ssh ${USUARIOS_CON_CLAVE[0]}@<ip>

Si entra, listo. Si no, con esta sesion todavia abierta podes revertir:

    sudo rm /etc/ssh/sshd_config.d/99-hardening.conf
    sudo systemctl reload ssh
========================================================================
FIN
