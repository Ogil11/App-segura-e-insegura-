#!/usr/bin/env bash
# =============================================================================
# enviar-ca-mitm.sh - Copia la CA de mitmproxy a la carpeta Descargas del
# emulador, para instalarla como certificado de USUARIO
# (ver docs/instalar-ca-mitm.md).
#   bash scripts/enviar-ca-mitm.sh            # copiar el certificado
#   bash scripts/enviar-ca-mitm.sh --ajustes  # además abre la pantalla de instalación
# =============================================================================
set -euo pipefail
source "$(dirname "$0")/_comun.sh"
requiere_dispositivo

CA="$HOME/.mitmproxy/mitmproxy-ca-cert.cer"
if [[ ! -f "$CA" ]]; then
  # mitmproxy crea su CA la primera vez que arranca.
  aviso "Aún no existe la CA de mitmproxy; se genera arrancando mitmdump un momento..."
  "$VBIN/mitmdump$EXE" --listen-port 18080 >/dev/null 2>&1 &
  pid=$!; sleep 6; kill "$pid" 2>/dev/null || true
fi
[[ -f "$CA" ]] || { error "No se pudo generar $CA"; exit 1; }

adb_dispositivo push "$(ruta_nativa "$CA")" /sdcard/Download/mitmproxy-ca-cert.cer
ok "Copiada a /sdcard/Download/mitmproxy-ca-cert.cer"
echo "Huella SHA-256 de la CA de mitmproxy:"
openssl x509 -in "$HOME/.mitmproxy/mitmproxy-ca-cert.pem" -noout -fingerprint -sha256 2>/dev/null || true

if [[ "${1:-}" == "--ajustes" ]]; then
  # Abre "Instalar certificados" (Ajustes > Seguridad > Más ajustes de seguridad > Cifrado y credenciales)
  "$ADB" shell am start -a android.credentials.INSTALL >/dev/null 2>&1 \
    || "$ADB" shell am start -a android.settings.SECURITY_SETTINGS >/dev/null
fi
