#!/usr/bin/env bash
# =============================================================================
# instalar-apps.sh - Compila (debug) e instala las dos apps en el emulador.
#   bash scripts/instalar-apps.sh            # ambas
#   bash scripts/instalar-apps.sh vulnerable # solo una
# Se usa el build DEBUG de ambas para poder mostrar "run-as" en la demo 1.
# =============================================================================
set -euo pipefail
source "$(dirname "$0")/_comun.sh"
requiere_dispositivo

[[ -f "$RAIZ/vulnerable/app/src/main/res/raw/demo_ca.pem" ]] || bash "$RAIZ/scripts/gen-certs.sh"

APPS=("${@:-vulnerable segura}")
for app in ${APPS[@]}; do
  titulo "Compilando e instalando $app (debug)"
  (cd "$RAIZ/$app" && ./gradlew -q assembleDebug)
  "$ADB" install -r "$RAIZ/$app/app/build/outputs/apk/debug/app-debug.apk"
done
ok "Listo. Abre 'MiBanco Vulnerable' (rojo) y 'MiBanco Segura' (verde) en el emulador."
