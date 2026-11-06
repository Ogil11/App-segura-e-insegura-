#!/usr/bin/env bash
# =============================================================================
# mitm-passthrough.sh - SIN ATAQUE: mitmweb en el puerto 8080 pero dejando
# pasar el TLS de api.mibanco.test SIN interceptarlo (--ignore-hosts).
#
# El proxy solo reenvía bytes cifrados (túnel CONNECT): el teléfono habla TLS
# directamente con el backend real. Sirve para demostrar que la app SEGURA
# funciona con normalidad cuando no hay intercepción.
# En la UI (http://127.0.0.1:8081) no se verá el contenido de esas conexiones.
# =============================================================================
set -euo pipefail
source "$(dirname "$0")/_comun.sh"

MITMWEB="$VBIN/mitmweb$EXE"
[[ -f "$MITMWEB" ]] || { error "No está mitmweb. Ejecuta scripts/setup-env.sh"; exit 1; }

titulo "mitmweb en MODO PASSTHROUGH para $HOST_DEMO en :$PUERTO_PROXY (UI http://127.0.0.1:8081)"
# mitm_registro.py no imprimirá nada de api.mibanco.test: el proxy no puede verlo.
exec "$MITMWEB" \
  -s "$(ruta_nativa "$RAIZ/scripts/mitm_registro.py")" \
  --listen-host 127.0.0.1 --listen-port "$PUERTO_PROXY" \
  --web-host 127.0.0.1 --web-port 8081 \
  --ignore-hosts '^api\.mibanco\.test(:\d+)?$' \
  "$@"
