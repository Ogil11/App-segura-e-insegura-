#!/usr/bin/env bash
# =============================================================================
# start-emulator.sh - Arranca el AVD "mibanco_api34" y hace que TODO su tráfico
# HTTP/HTTPS pase por el proxy de la PC (mitmproxy en 127.0.0.1:8080).
#
#   bash scripts/start-emulator.sh               # con proxy (lo normal en la demo)
#   bash scripts/start-emulator.sh --sin-proxy   # quita el proxy
#   bash scripts/start-emulator.sh --emu-http-proxy   # método alternativo (ver nota)
#
# ¿Cómo se configura el proxy?
#   Se fija el proxy GLOBAL de Android a 10.0.2.2:8080 (10.0.2.2 es la IP con la
#   que el emulador ve el 127.0.0.1 de la PC):
#       adb shell settings put global http_proxy 10.0.2.2:8080
#   Así las apps envían "CONNECT api.mibanco.test:8443" al proxy, y es mitmproxy
#   (en la PC) quien resuelve api.mibanco.test usando el archivo hosts.
#
# Nota sobre "-http-proxy http://127.0.0.1:8080" (opción del emulador):
#   con esa opción Android NO recibe un proxy configurado: el emulador desvía las
#   conexiones TCP ya hechas, de modo que el teléfono tiene que resolver
#   api.mibanco.test por su cuenta, y el DNS del emulador NO consulta el archivo
#   hosts de la PC -> "UnknownHostException". Además no se puede combinar con el
#   proxy global (bucle). Por eso el método por defecto es el proxy global.
# =============================================================================
set -euo pipefail
source "$(dirname "$0")/_comun.sh"

MODO="${1:-proxy}"
ARGS=(-avd "$AVD_NOMBRE" -no-boot-anim -netdelay none -netspeed full)
[[ "$MODO" == "--emu-http-proxy" ]] && ARGS+=(-http-proxy "http://127.0.0.1:$PUERTO_PROXY")

if "$ADB" devices 2>/dev/null | grep -q '^emulator-'; then
  aviso "Ya hay un emulador corriendo; solo se ajusta el proxy."
else
  titulo "Arrancando $AVD_NOMBRE ..."
  mkdir -p "$RAIZ/out"; LOG="$RAIZ/out/emulador.log"
  nohup "$EMULATOR" "${ARGS[@]}" >"$LOG" 2>&1 &
  echo "Registro del emulador: $LOG"
  "$ADB" wait-for-device
  printf "Esperando a que Android termine de arrancar"
  listo=0
  for _ in $(seq 1 180); do
    if [[ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then
      listo=1; break
    fi
    printf "."; sleep 2
  done
  echo
  [[ $listo == 1 ]] || { error "El emulador no arrancó en 6 minutos. Revisa $LOG"; exit 1; }
  ok "Emulador listo."
fi

case "$MODO" in
  --sin-proxy|--emu-http-proxy)
    "$ADB" shell settings put global http_proxy :0
    ok "Proxy global de Android desactivado." ;;
  *)
    "$ADB" shell settings put global http_proxy "10.0.2.2:$PUERTO_PROXY"
    ok "Proxy global de Android: $("$ADB" shell settings get global http_proxy | tr -d '\r') (= 127.0.0.1:$PUERTO_PROXY de la PC)"
    aviso "Deja corriendo mitm-intercept.sh o mitm-passthrough.sh: sin proxy activo el emulador no tendrá red." ;;
esac
