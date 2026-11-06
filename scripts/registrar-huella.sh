#!/usr/bin/env bash
# =============================================================================
# registrar-huella.sh - Ayuda a registrar una huella en el emulador.
#
#  1. Si el emulador no tiene bloqueo de pantalla, pone el PIN 1234
#     (PIN de DEMOSTRACIÓN; Android exige un PIN antes de registrar huellas).
#  2. Abre la pantalla de registro de huella.
#  3. Tú: escribe el PIN 1234 y pulsa MORE / I AGREE (o "Acepto").
#  4. Este script simula el dedo 1 tocando el sensor hasta que termine.
# =============================================================================
set -euo pipefail
source "$(dirname "$0")/_comun.sh"
requiere_dispositivo

if "$ADB" shell locksettings get-disabled 2>/dev/null | tr -d '\r' | grep -q true \
   || ! "$ADB" shell locksettings verify --old 1234 >/dev/null 2>&1; then
  "$ADB" shell locksettings set-pin 1234 >/dev/null 2>&1 \
    && ok "PIN de bloqueo del emulador: 1234 (demo)" \
    || aviso "Ya hay un bloqueo de pantalla distinto; usa tu PIN."
fi

"$ADB" shell am start -a android.settings.FINGERPRINT_ENROLL >/dev/null 2>&1 \
  || "$ADB" shell am start -a android.settings.BIOMETRIC_ENROLL >/dev/null
echo "En el emulador: escribe el PIN (1234), luego pulsa MORE hasta I AGREE."
echo "Este script tocará el sensor automáticamente durante 2 minutos..."

actividad() { "$ADB" shell dumpsys activity activities 2>/dev/null | grep -m1 topResumedActivity | tr -d '\r'; }
for _ in $(seq 1 120); do
  a="$(actividad)"
  if [[ "$a" == *FingerprintEnrollEnrolling* ]]; then
    "$ADB" -e emu finger touch 1 >/dev/null
  elif [[ "$a" == *FingerprintEnrollFinish* ]]; then
    ok "¡Huella registrada! Pulsa DONE en el emulador."; exit 0
  fi
  sleep 1
done
aviso "No se detectó el final del registro. Puedes tocar el sensor a mano con: bash scripts/huella.sh"
