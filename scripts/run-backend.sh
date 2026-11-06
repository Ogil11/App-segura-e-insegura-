#!/usr/bin/env bash
# =============================================================================
# run-backend.sh - Levanta el backend HTTPS de prueba en https://api.mibanco.test:8443
# Requiere: setup-env.sh (venv) y gen-certs.sh (certificados).
# Ctrl+C para detenerlo.
# =============================================================================
set -euo pipefail
source "$(dirname "$0")/_comun.sh"

[[ -f "$PY_VENV" ]] || { error "No existe el venv. Ejecuta: bash scripts/setup-env.sh"; exit 1; }
[[ -f "$RAIZ/certs/server.crt" ]] || bash "$RAIZ/scripts/gen-certs.sh"

titulo "Backend MiBanco (Ctrl+C para detener)"
export PYTHONUNBUFFERED=1 PYTHONIOENCODING=utf-8
exec "$PY_VENV" "$RAIZ/backend/servidor.py"
