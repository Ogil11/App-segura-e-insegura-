#!/usr/bin/env bash
# =============================================================================
# setup-env.sh - FASE 0: verifica e instala todo lo necesario para la demo.
#
#   JDK 17, Android SDK (cmdline-tools, platform-tools, platforms;android-35,
#   build-tools;35.0.0, emulator, system image API 34), Python 3.12+ con venv
#   (Flask + mitmproxy), openssl, jadx, git; acepta licencias y crea el AVD
#   "mibanco_api34" (Pixel, Android 14, Google APIs, 2 GB de RAM).
#
# Uso:  bash scripts/setup-env.sh
# Es idempotente: si algo ya está instalado, solo muestra su versión.
# Si algo necesita sudo/administrador, muestra el comando y se detiene.
# =============================================================================
set -euo pipefail
source "$(dirname "$0")/_comun.sh"

JADX_VERSION="1.5.6"   # última estable en GitHub (skylot/jadx) a nov-2026

necesita_admin() {
  error "Esto requiere permisos de administrador. Ejecuta tú mismo:"
  echo "    $*"
  echo "y vuelve a lanzar este script."
  exit 1
}

titulo "Sistema detectado: $SO / CPU $(uname -m) -> ABI del emulador: $ABI"
echo "ANDROID_HOME = $ANDROID_HOME"
[[ -n "${ANDROID_AVD_HOME:-}" ]] && echo "ANDROID_AVD_HOME = $ANDROID_AVD_HOME"

# -----------------------------------------------------------------------------
titulo "git"
if command -v git >/dev/null; then ok "$(git --version)"; else
  case "$SO" in
    windows) winget install -e --id Git.Git --accept-source-agreements --accept-package-agreements ;;
    mac)     brew install git ;;
    *)       necesita_admin "sudo apt-get install -y git   (o: sudo dnf install -y git)" ;;
  esac
fi

# -----------------------------------------------------------------------------
titulo "openssl"
if command -v openssl >/dev/null; then ok "$(openssl version)"; else
  case "$SO" in
    windows) error "Git Bash trae openssl; reinstala Git for Windows."; exit 1 ;;
    mac)     brew install openssl ;;
    *)       necesita_admin "sudo apt-get install -y openssl" ;;
  esac
fi

# -----------------------------------------------------------------------------
titulo "JDK 17"
java_ok() { java -version 2>&1 | grep -qE 'version "17\.'; }
if ! java_ok; then
  case "$SO" in
    windows) winget install -e --id EclipseAdoptium.Temurin.17.JDK --accept-source-agreements --accept-package-agreements
             aviso "Cierra y abre la terminal para que JAVA esté en el PATH, y vuelve a ejecutar." ; exit 1 ;;
    mac)     brew install --cask temurin@17 ;;
    *)       necesita_admin "sudo apt-get install -y openjdk-17-jdk   (o: sudo dnf install -y java-17-openjdk-devel)" ;;
  esac
fi
ok "$(java -version 2>&1 | head -1)"

# -----------------------------------------------------------------------------
titulo "Android SDK: cmdline-tools"
if [[ ! -x "$SDKMANAGER" && ! -f "$SDKMANAGER" ]]; then
  case "$SO" in windows) plat="win" ;; mac) plat="mac" ;; *) plat="linux" ;; esac
  # Busca el número de la versión más reciente en el índice oficial de Google.
  zip=$(curl -fsSL https://dl.google.com/android/repository/repository2-3.xml \
        | grep -o "commandlinetools-${plat}-[0-9]*_latest.zip" | sort -t- -k3 -n | tail -1)
  echo "Descargando $zip ..."
  tmp="$(mktemp -d)"
  curl -fL --progress-bar -o "$tmp/clt.zip" "https://dl.google.com/android/repository/$zip"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  unzip -q "$tmp/clt.zip" -d "$tmp"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -rf "$tmp"
fi
ok "sdkmanager $("$SDKMANAGER" --version 2>/dev/null | tr -d '\r')"

# -----------------------------------------------------------------------------
titulo "Android SDK: licencias y paquetes"
IMG="system-images;android-34;google_apis;$ABI"
# build-tools;36.0.0: AGP 9.5 exige >= 36 (35.0.0 se instala porque lo pide la guía).
PAQUETES=("platform-tools" "platforms;android-35" "build-tools;35.0.0" "build-tools;36.0.0" "emulator" "$IMG")
yes 2>/dev/null | "$SDKMANAGER" --licenses >/dev/null || true
"$SDKMANAGER" --install "${PAQUETES[@]}"
ok "Paquetes instalados:"
"$SDKMANAGER" --list_installed 2>/dev/null | tr -d '\r' | grep -E 'platform-tools|platforms;|build-tools|emulator|system-images' || true
ok "$("$ADB" version | head -1 | tr -d '\r')"
ok "Emulador $("$EMULATOR" -version 2>/dev/null | head -1 | tr -d '\r')"

# local.properties para que ./gradlew encuentre el SDK sin variables de entorno.
for app in vulnerable segura; do
  [[ -d "$RAIZ/$app" ]] && echo "sdk.dir=$ANDROID_HOME" > "$RAIZ/$app/local.properties"
done
ok "local.properties -> sdk.dir=$ANDROID_HOME"

# -----------------------------------------------------------------------------
titulo "AVD $AVD_NOMBRE"
[[ -n "${ANDROID_AVD_HOME:-}" ]] && mkdir -p "$ANDROID_AVD_HOME"
if "$EMULATOR" -list-avds 2>/dev/null | tr -d '\r' | grep -qx "$AVD_NOMBRE"; then
  ok "El AVD ya existe."
else
  echo "no" | "$AVDMANAGER" create avd -n "$AVD_NOMBRE" -k "$IMG" -d pixel --force
fi
# Ajustes del AVD: 2 GB de RAM, teclado físico y sensor de huella.
CFG="${ANDROID_AVD_HOME:-$HOME/.android/avd}/$AVD_NOMBRE.avd/config.ini"
if [[ -f "$CFG" ]]; then
  for kv in "hw.ramSize=2048" "hw.keyboard=yes" "hw.fingerprint=yes" "hw.gpu.enabled=yes" "hw.gpu.mode=auto"; do
    k="${kv%%=*}"
    if grep -q "^$k" "$CFG"; then sed -i "s|^$k.*|$kv|" "$CFG"; else echo "$kv" >> "$CFG"; fi
  done
  ok "config.ini: $(grep -E '^hw.ramSize' "$CFG")"
fi

# -----------------------------------------------------------------------------
titulo "Python 3.12+ y venv (Flask + mitmproxy)"
if ! PYSYS="$(python_sistema)"; then
  case "$SO" in
    windows) winget install -e --id Python.Python.3.13 --scope user --accept-source-agreements --accept-package-agreements
             PYSYS="$(python_sistema)" || { aviso "Reabre la terminal y vuelve a ejecutar."; exit 1; } ;;
    mac)     brew install python@3.13; PYSYS="python3.13" ;;
    *)       necesita_admin "sudo apt-get install -y python3.12 python3.12-venv" ;;
  esac
fi
ok "Python del sistema: $($PYSYS --version) ($PYSYS)"
if [[ ! -f "$PY_VENV" ]]; then
  $PYSYS -m venv "$VENV"
fi
"$PY_VENV" -m pip install --quiet --upgrade pip
"$PY_VENV" -m pip install --quiet -r "$RAIZ/backend/requirements.txt"
ok "venv: $("$PY_VENV" --version)  Flask $("$PY_VENV" -c 'import importlib.metadata as m; print(m.version("flask"))')"
ok "$("$VBIN/mitmdump$EXE" --version 2>/dev/null | head -1)"

# -----------------------------------------------------------------------------
titulo "jadx $JADX_VERSION"
if [[ ! -f "$JADX" ]]; then
  tmp="$(mktemp -d)"
  curl -fL --progress-bar -o "$tmp/jadx.zip" \
    "https://github.com/skylot/jadx/releases/download/v$JADX_VERSION/jadx-$JADX_VERSION.zip"
  rm -rf "$RAIZ/tools/jadx"; mkdir -p "$RAIZ/tools/jadx"
  unzip -q "$tmp/jadx.zip" -d "$RAIZ/tools/jadx"
  chmod +x "$RAIZ/tools/jadx/bin/"* || true
  rm -rf "$tmp"
fi
ok "jadx $("$JADX" --version 2>/dev/null | tr -d '\r')"

titulo "Entorno listo."
