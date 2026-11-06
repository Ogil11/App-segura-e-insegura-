#!/usr/bin/env bash
# =============================================================================
# mitm-intercept.sh - ATAQUE: mitmweb en el puerto 8080 interceptando TLS.
#
# mitmproxy presenta al teléfono un certificado falso de api.mibanco.test
# firmado por SU propia CA. Si esa CA está instalada como certificado de
# USUARIO en el emulador (docs/instalar-ca-mitm.md):
#   - App VULNERABLE: confía en CAs de usuario -> se ve TODO (login, token, API key).
#   - App SEGURA: no confía en CAs de usuario + pinning -> la conexión FALLA.
#
# Interfaz web: http://127.0.0.1:8081   (Ctrl+C para detener)
# =============================================================================
set -euo pipefail
source "$(dirname "$0")/_comun.sh"

MITMWEB="$VBIN/mitmweb$EXE"
[[ -f "$MITMWEB" ]] || { error "No está mitmweb. Ejecuta scripts/setup-env.sh"; exit 1; }

titulo "mitmweb INTERCEPTANDO TLS en :$PUERTO_PROXY (UI en http://127.0.0.1:8081)"
echo "CA de mitmproxy (para instalar en el emulador): $HOME/.mitmproxy/mitmproxy-ca-cert.cer"
# ssl_verify_upstream_trusted_ca: mitmproxy valida el backend real con la CA de demo.
# mitm_registro.py imprime en esta terminal lo interceptado (contraseña, token, API key).
exec "$MITMWEB" \
  -s "$(ruta_nativa "$RAIZ/scripts/mitm_registro.py")" \
  --listen-host 127.0.0.1 --listen-port "$PUERTO_PROXY" \
  --web-host 127.0.0.1 --web-port 8081 \
  --set ssl_verify_upstream_trusted_ca="$(ruta_nativa "$RAIZ/certs/ca.crt")" \
  "$@"
