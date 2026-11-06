#!/usr/bin/env bash
# =============================================================================
# mitm-pinning.sh - DEMO EXTRA: aísla el efecto del certificate pinning.
#
# Escenario: el atacante consiguió un certificado VÁLIDO para api.mibanco.test,
# emitido por una CA en la que la app SÍ confía (aquí, la propia CA de demo;
# en la vida real: una CA comprometida o mal emitida), pero con OTRA llave.
#
#   - App VULNERABLE: la cadena es válida -> la intercepción funciona.
#   - App SEGURA: la cadena también es válida, PERO la llave no coincide con
#     ningún <pin> -> "Pin verification failed" -> no se conecta.
#
# Genera certs/impostor.pem (llave + certificado) la primera vez.
# Interfaz web: http://127.0.0.1:8081   (Ctrl+C para detener)
# =============================================================================
set -euo pipefail
source "$(dirname "$0")/_comun.sh"

MITMWEB="$VBIN/mitmweb$EXE"
[[ -f "$MITMWEB" ]] || { error "No está mitmweb. Ejecuta scripts/setup-env.sh"; exit 1; }
[[ -f "$RAIZ/certs/ca.key" ]] || bash "$RAIZ/scripts/gen-certs.sh"

C="$RAIZ/certs"
if [[ ! -f "$C/impostor.pem" ]]; then
  titulo "Generando certificado 'impostor' para $HOST_DEMO (firmado por la CA de demo, llave nueva)"
  (
    cd "$C"
    openssl ecparam -name prime256v1 -genkey -noout -out impostor.key
    openssl req -new -key impostor.key -config server.cnf -out impostor.csr
    openssl x509 -req -in impostor.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
      -days 30 -sha256 -extfile server.cnf -extensions v3_srv -out impostor.crt 2>/dev/null
    cat impostor.key impostor.crt > impostor.pem
    rm -f impostor.csr ca.srl
  )
fi
PIN_IMPOSTOR="$(openssl pkey -in "$C/impostor.key" -pubout -outform DER | openssl dgst -sha256 -binary | openssl base64 -A)"
echo "Pin de la llave impostora: $PIN_IMPOSTOR  (NO está en el pin-set de la app segura)"
grep -E 'PIN_' "$C/pines.txt" | sed 's/^/Pines aceptados: /'

titulo "mitmweb con certificado impostor en :$PUERTO_PROXY"
exec "$MITMWEB" \
  -s "$(ruta_nativa "$RAIZ/scripts/mitm_registro.py")" \
  --listen-host 127.0.0.1 --listen-port "$PUERTO_PROXY" \
  --web-host 127.0.0.1 --web-port 8081 \
  --certs "$HOST_DEMO=$(ruta_nativa "$C/impostor.pem")" \
  --set ssl_verify_upstream_trusted_ca="$(ruta_nativa "$C/ca.crt")" \
  "$@"
