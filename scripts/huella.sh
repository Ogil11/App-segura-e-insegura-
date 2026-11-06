#!/usr/bin/env bash
# =============================================================================
# huella.sh - Simula que el dedo registrado (id 1) toca el sensor del emulador.
# Úsalo cuando el emulador pida la huella (al registrarla en Ajustes y cuando
# la app segura muestre el diálogo biométrico).
#   bash scripts/huella.sh      # dedo 1 (el registrado)
#   bash scripts/huella.sh 2    # otro dedo -> "huella no reconocida"
# =============================================================================
set -euo pipefail
source "$(dirname "$0")/_comun.sh"
requiere_dispositivo
"$ADB" -e emu finger touch "${1:-1}" | tr -d '\r'
