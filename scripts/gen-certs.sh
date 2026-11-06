#!/usr/bin/env bash
# =============================================================================
# gen-certs.sh - Genera la PKI de DEMOSTRACIÓN en certs/:
#
#   certs/ca.key, ca.crt          CA de demo "MiBanco Demo CA" (solo laboratorio)
#   certs/server.key, server.crt  certificado de api.mibanco.test (SAN correcto,
#                                 validez 397 días < 825)
#   certs/backup.key              segunda llave, NO usada aún: su pin es el
#                                 "backup pin" para poder rotar sin romper la app
#   certs/pines.txt               pines SPKI SHA-256 en base64
#
# Además:
#   - copia ca.crt a res/raw/demo_ca.pem de ambas apps
#   - REGENERA segura/app/src/main/res/xml/network_security_config.xml con los
#     pines nuevos (así nunca hay que pegarlos a mano).
#
# Las llaves privadas NO se commitean (certs/ está en .gitignore).
# Todo es FALSO y de demostración: no uses estos certificados fuera del laboratorio.
#
# Uso: bash scripts/gen-certs.sh [--force]
# Después de regenerar, recompila e instala las dos apps.
# =============================================================================
set -euo pipefail
source "$(dirname "$0")/_comun.sh"

# Nota: los datos del certificado van en archivos .cnf (no con -subj "/CN=...")
# para que Git Bash en Windows no confunda "/CN=" con una ruta.

CERTS="$RAIZ/certs"
DIAS_SERVIDOR=397     # < 825 días (límite de Apple/CAB) y < 398 (navegadores)
DIAS_CA=1825

if [[ -f "$CERTS/server.crt" && "${1:-}" != "--force" ]]; then
  aviso "Ya existen certificados en certs/. Se reutilizan (usa --force para regenerarlos)."
else
  rm -rf "$CERTS"; mkdir -p "$CERTS"
  cd "$CERTS"

  titulo "1) CA de demostración"
  openssl ecparam -name prime256v1 -genkey -noout -out ca.key
  cat > ca.cnf <<'EOF'
[req]
distinguished_name = dn
prompt = no
x509_extensions = v3_ca
[dn]
CN = MiBanco Demo CA (SOLO DEMO)
O  = Exposicion Seguridad Movil
[v3_ca]
basicConstraints = critical, CA:TRUE, pathlen:0
keyUsage = critical, keyCertSign, cRLSign
subjectKeyIdentifier = hash
EOF
  openssl req -x509 -new -key ca.key -sha256 -days "$DIAS_CA" -config ca.cnf -out ca.crt

  titulo "2) Certificado de servidor para $HOST_DEMO"
  openssl ecparam -name prime256v1 -genkey -noout -out server.key
  cat > server.cnf <<EOF
[req]
distinguished_name = dn
prompt = no
[dn]
CN = $HOST_DEMO
O  = MiBanco (FICTICIO)
[v3_srv]
basicConstraints = critical, CA:FALSE
keyUsage = critical, digitalSignature
extendedKeyUsage = serverAuth
subjectAltName = DNS:$HOST_DEMO
authorityKeyIdentifier = keyid
subjectKeyIdentifier = hash
EOF
  openssl req -new -key server.key -config server.cnf -out server.csr
  openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
    -days "$DIAS_SERVIDOR" -sha256 -extfile server.cnf -extensions v3_srv -out server.crt
  rm -f server.csr ca.srl

  titulo "3) Llave de respaldo (backup pin)"
  openssl ecparam -name prime256v1 -genkey -noout -out backup.key
  cd "$RAIZ"
fi

# Pin SPKI = base64( SHA-256( SubjectPublicKeyInfo en DER ) )
pin_de_llave() {
  openssl pkey -in "$1" -pubout -outform DER 2>/dev/null \
    | openssl dgst -sha256 -binary | openssl base64 -A
}
PIN_SERVIDOR="$(openssl x509 -in "$CERTS/server.crt" -pubkey -noout \
  | openssl pkey -pubin -outform DER | openssl dgst -sha256 -binary | openssl base64 -A)"
PIN_RESPALDO="$(pin_de_llave "$CERTS/backup.key")"

# Caducidad del pin-set = fecha de expiración del certificado del servidor.
FIN_CERT="$(openssl x509 -in "$CERTS/server.crt" -noout -enddate | cut -d= -f2)"
if date -d "$FIN_CERT" +%F >/dev/null 2>&1; then
  EXPIRA="$(date -d "$FIN_CERT" +%F)"                                  # GNU date
else
  EXPIRA="$(date -j -f "%b %e %T %Y %Z" "$FIN_CERT" +%F)"               # macOS
fi

cat > "$CERTS/pines.txt" <<EOF
PIN_SERVIDOR=$PIN_SERVIDOR
PIN_RESPALDO=$PIN_RESPALDO
EXPIRA=$EXPIRA
EOF

titulo "Certificado del servidor"
openssl x509 -in "$CERTS/server.crt" -noout -subject -issuer -dates -ext subjectAltName

titulo "Pines SPKI SHA-256 (base64)"
echo "  Servidor (principal): $PIN_SERVIDOR"
echo "  Respaldo (backup):    $PIN_RESPALDO"
echo "  Expiración pin-set:   $EXPIRA"

# ---- Copiar la CA de demo a ambas apps (res/raw) ----
for app in vulnerable segura; do
  dest="$RAIZ/$app/app/src/main/res/raw"
  mkdir -p "$dest"
  cp "$CERTS/ca.crt" "$dest/demo_ca.pem"
done
ok "CA copiada a vulnerable/ y segura/ (res/raw/demo_ca.pem)"

# ---- Regenerar el network_security_config.xml de la app SEGURA ----
NSC="$RAIZ/segura/app/src/main/res/xml/network_security_config.xml"
mkdir -p "$(dirname "$NSC")"
cat > "$NSC" <<EOF
<?xml version="1.0" encoding="utf-8"?>
<!--
  ARCHIVO GENERADO por scripts/gen-certs.sh (no editar los pines a mano:
  vuelve a ejecutar el script y recompila la app).

  SEGURO: configuración de red estricta (MASVS-NETWORK-1, MASVS-NETWORK-2; OWASP M5).
-->
<network-security-config>

    <!-- SEGURO: prohíbe HTTP en claro y confía SOLO en las CAs del sistema.
         No hay <certificates src="user"/>: una CA instalada por el usuario
         (p. ej. la de mitmproxy) NO sirve para interceptar esta app. -->
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>

    <!-- SEGURO: para el backend de la demo se confía únicamente en la CA de
         laboratorio (res/raw/demo_ca.pem) y además se FIJA (pinning) la llave
         pública del servidor. Aunque alguien consiga un certificado válido de
         otra CA, la conexión falla si la llave no coincide con estos pines. -->
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="false">$HOST_DEMO</domain>
        <trust-anchors>
            <certificates src="@raw/demo_ca" />
        </trust-anchors>
        <!-- Caduca con el certificado: pasada esa fecha Android deja de aplicar
             el pinning (evita "brickear" la app si no se actualiza a tiempo). -->
        <pin-set expiration="$EXPIRA">
            <!-- Pin principal: llave del certificado actual del servidor -->
            <pin digest="SHA-256">$PIN_SERVIDOR</pin>
            <!-- Pin de respaldo: llave de reserva (certs/backup.key) para rotar
                 el certificado sin publicar una nueva versión de la app -->
            <pin digest="SHA-256">$PIN_RESPALDO</pin>
        </pin-set>
    </domain-config>

    <!-- SEGURO: sin <debug-overrides>: ni siquiera el build debug confía en CAs de usuario. -->
</network-security-config>
EOF
ok "Generado $NSC"
aviso "Recompila e instala de nuevo las dos apps para que usen estos certificados."
