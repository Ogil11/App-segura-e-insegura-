#!/usr/bin/env bash
# =============================================================================
# _comun.sh - Variables y funciones compartidas por todos los scripts.
# Se carga con:  source "$(dirname "$0")/_comun.sh"
# =============================================================================

# Raíz del repositorio (carpeta padre de scripts/)
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# ---- Detección de sistema operativo y CPU ----
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) SO="windows" ;;
  Darwin*)              SO="mac" ;;
  Linux*)               SO="linux" ;;
  *)                    SO="desconocido" ;;
esac

case "$(uname -m)" in
  x86_64|amd64)  ABI="x86_64" ;;
  arm64|aarch64) ABI="arm64-v8a" ;;
  *)             ABI="x86_64" ;;
esac

# ---- Ubicación del Android SDK ----
# En Windows se usa C:\Android para evitar rutas con espacios (el emulador y
# algunas herramientas del SDK fallan con rutas como "C:\Users\NOMBRE  APELLIDO").
if [[ -z "${ANDROID_HOME:-}" ]]; then
  case "$SO" in
    windows) ANDROID_HOME="C:/Android/Sdk" ;;
    mac)     ANDROID_HOME="$HOME/Library/Android/sdk" ;;
    *)       ANDROID_HOME="$HOME/Android/Sdk" ;;
  esac
fi
export ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"

# Carpeta de los AVD (también sin espacios en Windows)
if [[ -z "${ANDROID_AVD_HOME:-}" && "$SO" == "windows" ]]; then
  export ANDROID_AVD_HOME="C:/Android/avd"
fi

# Extensión de los ejecutables .bat en Windows
if [[ "$SO" == "windows" ]]; then BAT=".bat"; EXE=".exe"; else BAT=""; EXE=""; fi

SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager$BAT"
AVDMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager$BAT"
ADB="$ANDROID_HOME/platform-tools/adb$EXE"
EMULATOR="$ANDROID_HOME/emulator/emulator$EXE"

# ---- Python / venv del proyecto (Flask + mitmproxy) ----
VENV="$RAIZ/.venv"
if [[ "$SO" == "windows" ]]; then VBIN="$VENV/Scripts"; else VBIN="$VENV/bin"; fi
PY_VENV="$VBIN/python$EXE"

# ---- jadx (se descarga en tools/) ----
JADX="$RAIZ/tools/jadx/bin/jadx$BAT"

# ---- Datos de la demo ----
AVD_NOMBRE="mibanco_api34"
HOST_DEMO="api.mibanco.test"
PUERTO_BACKEND=8443
PUERTO_PROXY=8080
PKG_VULN="com.demo.mibanco"
PKG_SEGURA="com.demo.mibanco.segura"

# ---- Utilidades de salida ----
titulo() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
ok()     { printf '\033[1;32m[OK]\033[0m %s\n' "$*"; }
aviso()  { printf '\033[1;33m[AVISO]\033[0m %s\n' "$*"; }
error()  { printf '\033[1;31m[ERROR]\033[0m %s\n' "$*" >&2; }

# Busca un Python 3.12+ del sistema para crear el venv.
# (Flask funciona con 3.10+, pero mitmproxy 12.x exige Python >= 3.12.)
# Imprime el comando a usar (p.ej. "py -3.13" o "python3").
python_sistema() {
  local c
  local candidatos=()
  [[ "$SO" == "windows" ]] && candidatos+=("py -3.14" "py -3.13" "py -3.12")
  candidatos+=("python3.14" "python3.13" "python3.12" "python3" "python")
  for c in "${candidatos[@]}"; do
    if $c -c 'import sys; sys.exit(0 if sys.version_info >= (3,12) else 1)' >/dev/null 2>&1; then
      echo "$c"; return 0
    fi
  done
  return 1
}

# Ruta de la PC en formato nativo (C:/...) para pasarla a programas de Windows.
ruta_nativa() {
  if [[ "$SO" == "windows" ]]; then cygpath -m "$1"; else echo "$1"; fi
}

# adb para comandos con rutas DEL DISPOSITIVO (/sdcard/...): en Git Bash evita
# que MSYS las convierta en rutas de Windows ("C:/Program Files/Git/sdcard/...").
# Las rutas de la PC que se le pasen deben ir con ruta_nativa.
adb_dispositivo() {
  MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL="*" "$ADB" "$@"
}

# Comprueba que adb exista y haya un emulador conectado
requiere_dispositivo() {
  if [[ ! -x "$ADB" ]]; then error "No se encontró adb en $ADB. Ejecuta scripts/setup-env.sh"; exit 1; fi
  if ! "$ADB" get-state >/dev/null 2>&1; then
    error "No hay emulador conectado. Ejecuta scripts/start-emulator.sh"; exit 1
  fi
}
