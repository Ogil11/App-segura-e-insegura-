#!/usr/bin/env bash
# =============================================================================
# decompilar.sh - Genera el APK release de cada app, lo decompila con jadx y
# busca la API key.
#
#   - VULNERABLE (sin R8): aparece  const API_KEY = "DEMO-FAKE-KEY-1234567890"
#   - SEGURA (R8 activo y SIN secretos): no aparece nada. Aunque se quitara R8,
#     tampoco habría clave: la sesión la emite el backend en /login.
#
# Salida en out/jadx-vulnerable/ y out/jadx-segura/ (puedes abrirlas en VS Code).
# =============================================================================
set -euo pipefail
source "$(dirname "$0")/_comun.sh"

JADX_JAR="$(ls "$RAIZ"/tools/jadx/lib/jadx-*-all.jar 2>/dev/null | head -1)"
[[ -n "$JADX_JAR" ]] || { error "No está jadx. Ejecuta scripts/setup-env.sh"; exit 1; }
# Se llama a jadx con java directamente: jadx.bat falla en Windows si la ruta
# del proyecto contiene espacios.
jadx() { java -Xmx2g -cp "$(ruta_nativa "$JADX_JAR")" jadx.cli.JadxCLI "$@"; }
mkdir -p "$RAIZ/out"

for app in vulnerable segura; do
  titulo "Compilando $app (release)"
  (cd "$RAIZ/$app" && ./gradlew -q assembleRelease)
  apk="$RAIZ/$app/app/build/outputs/apk/release/app-release.apk"
  dest="$RAIZ/out/jadx-$app"
  rm -rf "$dest"
  titulo "jadx -> $dest"
  jadx -q -d "$(ruta_nativa "$dest")" "$(ruta_nativa "$apk")" || true  # !=0 si algún método no decompila
  [[ -d "$dest/sources" ]] || { error "jadx no generó $dest/sources"; exit 1; }
done

titulo "Buscando API_KEY / X-API-Key en el código decompilado de la app VULNERABLE"
grep -rnE 'API_KEY|X-API-Key|DEMO-FAKE-KEY' "$RAIZ/out/jadx-vulnerable/sources/com/demo" || echo "(nada)"

titulo "Buscando API_KEY / X-API-Key en el código decompilado de la app SEGURA"
if grep -rnE 'API_KEY|X-API-Key|DEMO-FAKE-KEY' "$RAIZ/out/jadx-segura/sources"; then
  error "¡La app segura contiene la clave!"
else
  ok "(nada) -> la app segura no filtra ningún secreto"
fi

titulo "Efecto de R8: clases de la app en cada APK"
echo "VULNERABLE (nombres originales: AlmacenSesion, ApiCliente...):"
find "$RAIZ/out/jadx-vulnerable/sources/com/demo" -name '*.java' | sed "s|$RAIZ/out/||" | head -n 8
echo
echo "SEGURA (R8 renombró/fusionó casi todo; solo queda MainActivity por el Manifest):"
find "$RAIZ/out/jadx-segura/sources/com/demo" -name '*.java' | sed "s|$RAIZ/out/||"
echo "  + clases ofuscadas en out/jadx-segura/sources/defpackage/ (a.java, b.java, ...)"
echo
echo "Recuerda: la ofuscación solo DIFICULTA el análisis; no protege secretos."
