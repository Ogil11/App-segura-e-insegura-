#!/usr/bin/env bash
# =============================================================================
# ataque-almacenamiento.sh - DEMO 1: leer los datos que cada app guardó en disco.
#
#   adb shell run-as <paquete> cat shared_prefs/sesion.xml
#
# NOTA: "run-as" solo funciona con builds DEBUGGABLE (los de la demo lo son).
# En la vida real se obtiene exactamente lo mismo con un dispositivo rooteado
# (cat /data/data/<paquete>/shared_prefs/sesion.xml), con un backup
# (allowBackup=true) o con un análisis forense del teléfono.
# =============================================================================
set -uo pipefail
source "$(dirname "$0")/_comun.sh"
requiere_dispositivo

leer() {
  local pkg="$1"
  titulo "$pkg -> shared_prefs/sesion.xml"
  if ! "$ADB" shell pm path "$pkg" >/dev/null 2>&1; then
    aviso "La app $pkg no está instalada."; return
  fi
  local salida
  salida="$("$ADB" shell run-as "$pkg" cat shared_prefs/sesion.xml 2>&1 | tr -d '\r')"
  if [[ -z "$salida" || "$salida" == *"No such file"* ]]; then
    aviso "No hay sesion.xml todavía: inicia sesión en la app primero."
  else
    echo "$salida"
  fi
}

leer "$PKG_VULN"
echo
echo "   ^ VULNERABLE: usuario, CONTRASEÑA y token en texto plano."

leer "$PKG_SEGURA"
echo
echo "   ^ SEGURA: solo 'token_cifrado' = Base64(IV + AES-256-GCM). Sin la llave del"
echo "     Android Keystore (no exportable y protegida por huella) no sirve para nada."

titulo "Bonus: lo que la app vulnerable escribió en logcat"
"$ADB" logcat -d -s MiBancoApi:D 2>/dev/null | tail -n 3 | tr -d '\r' || true
