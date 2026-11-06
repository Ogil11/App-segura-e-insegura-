#!/usr/bin/env bash
# =============================================================================
# verificar-backend.sh - Prueba el backend con curl validando el certificado
# con la CA de demo (--cacert). No necesita el archivo hosts: usa --resolve.
# Requiere el backend corriendo (scripts/run-backend.sh).
# =============================================================================
set -euo pipefail
source "$(dirname "$0")/_comun.sh"

URL="https://$HOST_DEMO:$PUERTO_BACKEND"
C=(curl -sS --resolve "$HOST_DEMO:$PUERTO_BACKEND:127.0.0.1" --cacert "$RAIZ/certs/ca.crt")
# En Windows, el curl de Git usa schannel, que exige comprobar revocación (CRL);
# la CA de demo no publica CRL, así que se desactiva SOLO esa comprobación.
[[ "$SO" == "windows" ]] && C+=(--ssl-no-revoke)
JSON=(-H "Content-Type: application/json")

titulo "POST /login con contraseña incorrecta (esperado: 401)"
"${C[@]}" "${JSON[@]}" -X POST "$URL/login" -d '{"usuario":"demo","contrasena":"mal"}' -w '  [HTTP %{http_code}]\n'

titulo "POST /login con demo / demo1234 (esperado: 200 + token)"
RESP="$("${C[@]}" "${JSON[@]}" -X POST "$URL/login" -d '{"usuario":"demo","contrasena":"demo1234"}')"
echo "$RESP"
TOKEN="$(echo "$RESP" | sed -n 's/.*"token": *"\([^"]*\)".*/\1/p')"
[[ -n "$TOKEN" ]] || { error "No se recibió token"; exit 1; }

titulo "GET /balance sin token (esperado: 401)"
"${C[@]}" "$URL/balance" -w '  [HTTP %{http_code}]\n'

titulo "GET /balance con Bearer token (esperado: 200 + datos ficticios)"
"${C[@]}" "$URL/balance" -H "Authorization: Bearer $TOKEN" -w '  [HTTP %{http_code}]\n'

titulo "Sin --cacert (esperado: error de certificado no confiable)"
if curl -sS --resolve "$HOST_DEMO:$PUERTO_BACKEND:127.0.0.1" "$URL/" >/dev/null 2>&1; then
  error "¡curl aceptó el certificado sin la CA de demo!"; exit 1
else
  ok "Rechazado, como debe ser."
fi
ok "Backend verificado."
