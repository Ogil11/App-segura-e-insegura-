# MiBanco: app vulnerable vs. app segura (demo de seguridad móvil)

Repositorio para una **exposición universitaria sobre seguridad en Android**. Tiene dos
versiones de la misma app bancaria **ficticia**, MiBanco, que se ven y funcionan igual:

| | Paquete | Ícono | Qué es |
|---|---|---|---|
| `vulnerable/` | `com.demo.mibanco` | rojo, "MiBanco Vulnerable" | fallos hechos **a propósito**, cada uno marcado con `// VULNERABLE: ...` |
| `segura/` | `com.demo.mibanco.segura` | verde, "MiBanco Segura" | la misma app blindada, cada defensa marcada con `// SEGURO: ...` |

También incluye un backend HTTPS de prueba (`backend/`) y scripts (`scripts/`) para
demostrar **en vivo** dos ataques y sus defensas:

1. **Almacenamiento inseguro**: OWASP Mobile Top 10 2024 **M9** y **M1**; MASVS-STORAGE.
2. **Comunicación insegura**: OWASP **M5** y **M8**; MASVS-NETWORK.

Todo corre en el **emulador de Android**. No hace falta un teléfono real ni ninguna cuenta de pago.

> ⚠️ **Todo es falso y de demostración**: usuario `demo` / `demo1234`, token, API key
> `DEMO-FAKE-KEY-1234567890`, titular y cuenta. Las llaves privadas se generan en tu PC
> (`certs/`, que no se sube a git). Usa estas técnicas solo en tu laboratorio.

---

## Índice
1. [Estructura](#estructura)
2. [Requisitos e instalación](#requisitos-e-instalación)
3. [Preparación (una sola vez)](#preparación-una-sola-vez)
4. [Demo 1: almacenamiento inseguro](#demo-1-almacenamiento-inseguro)
5. [Demo 2: comunicación insegura](#demo-2-comunicación-insegura)
6. [Registrar una huella en el emulador](#registrar-una-huella-en-el-emulador)
7. [Tabla vulnerabilidad → defensa → referencia](#tabla-vulnerabilidad--defensa--referencia)
8. [Problemas comunes](#problemas-comunes)
9. [Notas técnicas y decisiones](#notas-técnicas-y-decisiones)

---

## Estructura

```
.
├── README.md                 esta guía
├── vulnerable/               proyecto Android independiente (Kotlin + Compose + OkHttp)
├── segura/                   proyecto Android independiente (+ Keystore, BiometricPrompt, pinning)
├── backend/                  servidor Flask HTTPS en https://api.mibanco.test:8443
├── certs/                    (generado) CA de demo, certificado del servidor, pines; NO se commitea
├── docs/instalar-ca-mitm.md  cómo instalar la CA de mitmproxy como certificado de usuario
├── scripts/                  setup, backend, emulador, mitmproxy, ataques, jadx...
└── out/                      (generado) logs, capturas y salida de jadx
```

## Requisitos e instalación

| Herramienta | Versión usada | Para qué |
|---|---|---|
| JDK | 17 (Temurin 17.0.7) | Gradle / Android |
| Android SDK | cmdline-tools 19, platform-tools, `platforms;android-35`, build-tools 35.0.0 y 36.0.0, emulador 37.2 | compilar y emular |
| Imagen del sistema | `system-images;android-34;google_apis;x86_64` (en Mac con Apple Silicon: `arm64-v8a`) | AVD `mibanco_api34` |
| Gradle | 9.9.0 (wrapper incluido en cada proyecto) | build |
| Python | 3.12 o superior (mitmproxy 12 lo exige), con venv en `.venv/` | backend + mitmproxy |
| Flask / mitmproxy | 3.1.3 / 12.3.1 | backend / ataque de red |
| openssl, git | los de Git for Windows | certificados |
| jadx | 1.5.6 (en `tools/jadx/`) | decompilar APKs |

**Todo esto lo instala o verifica un solo script:**

```bash
bash scripts/setup-env.sh
```

- Los scripts son de **bash**. En Windows, usa **Git Bash** o, desde PowerShell, el lanzador
  `.\scripts\run.ps1 <script> [argumentos]` (por ejemplo, `.\scripts\run.ps1 setup-env`).
- En Windows el SDK se instala en `C:\Android\Sdk` y los AVD en `C:\Android\avd`, porque
  rutas con espacios como `C:\Users\NOMBRE  APELLIDO` dan problemas al SDK y al emulador.
- Si algo necesita permisos de administrador, el script muestra el comando y se detiene.

**Versiones de las librerías** (fijadas en `gradle/libs.versions.toml`): AGP 9.5.1,
Kotlin 2.4.21, Compose BOM 2026.06.01, core-ktx 1.16.0, activity-compose 1.10.1,
fragment 1.9.1, biometric 1.1.0 y OkHttp 5.3.2. Son las estables más recientes **compatibles con
`compileSdk 35`**; ver [notas técnicas](#notas-técnicas-y-decisiones).

## Preparación (una sola vez)

```bash
# 1. Entorno (SDK, AVD, Python, mitmproxy, jadx)
bash scripts/setup-env.sh

# 2. PKI de demo: CA, certificado de api.mibanco.test y pines SPKI.
#    También copia la CA a ambas apps y REGENERA el network_security_config de la app segura.
bash scripts/gen-certs.sh
```

**3. Archivo hosts de la PC** (requiere administrador). Agrega esta línea:

```
127.0.0.1    api.mibanco.test
```

- Windows: `C:\Windows\System32\drivers\etc\hosts` (abre el Bloc de notas **como administrador**).
- Linux/macOS: `sudo sh -c 'echo "127.0.0.1    api.mibanco.test" >> /etc/hosts'`

Para comprobar el backend, en una terminal ejecuta `bash scripts/run-backend.sh` y en otra
`bash scripts/verificar-backend.sh`. Debe responder 401 a la contraseña incorrecta, 200 con
`token`, 401 a `/balance` sin token y 200 a `/balance` con token. Sin `--cacert`, el
certificado debe rechazarse.

**4. Emulador, apps y huella:**

```bash
bash scripts/start-emulator.sh      # arranca el AVD y fija el proxy global de Android en 10.0.2.2:8080
bash scripts/instalar-apps.sh       # compila (debug) e instala las dos apps
bash scripts/registrar-huella.sh    # PIN 1234 + huella (ver sección más abajo)
```

> **Cómo llega el tráfico del emulador al backend:** el emulador usa como proxy la PC
> (`10.0.2.2:8080` = `127.0.0.1:8080` de la PC), donde corre mitmproxy. mitmproxy resuelve
> `api.mibanco.test` con el archivo hosts y lo envía al backend (`127.0.0.1:8443`). Por eso,
> **durante la demo siempre debe estar corriendo uno de los scripts `mitm-*.sh`**; si no,
> el emulador se queda sin red.

### Terminales que conviene tener abiertas durante la exposición

| Terminal | Comando | Qué se ve |
|---|---|---|
| A | `bash scripts/run-backend.sh` | cada petición que llega al servidor real |
| B | `bash scripts/mitm-passthrough.sh` o `bash scripts/mitm-intercept.sh` | **en rojo**, lo que el atacante intercepta (también en http://127.0.0.1:8081) |
| C | para los comandos de ataque | |

---

## Demo 1: almacenamiento inseguro

**Idea:** cualquier cosa que la app guarde en el teléfono puede acabar en manos de un atacante
que tenga root, un backup o acceso forense al dispositivo.

Prepara el terreno con el backend (A) y `bash scripts/mitm-passthrough.sh` (B).

| # | Paso | Resultado esperado: VULNERABLE | Resultado esperado: SEGURA |
|---|---|---|---|
| 1 | Abre la app e inicia sesión con `demo` / `demo1234` | Entra directo al saldo ($ 2.450.380,75) | Pide la huella ("Proteger tu sesión"). Ejecuta `bash scripts/huella.sh` y aparece el saldo |
| 2 | Cierra la app (deslízala fuera de Recientes) y vuelve a abrirla | **Entra directo al saldo**, sin pedir nada | Abre **bloqueada** ("$ ••••••") y pide la huella. Si cancelas: "Autenticación cancelada. Tu saldo sigue protegido." |
| 3 | `bash scripts/ataque-almacenamiento.sh` | Ver bloque 1 abajo | Ver bloque 2 abajo |
| 4 | `bash scripts/decompilar.sh` | Ver bloque 3 abajo | Ver bloque 4 abajo |

**Bloque 1: `sesion.xml` de la app vulnerable (paso 3).** El usuario, la contraseña y el token
aparecen en texto plano:
```xml
<string name="usuario">demo</string>
<string name="contrasena">demo1234</string>
<string name="token">VsZ70RoZgeEF...</string>
```
El token también aparece en **logcat**: `D MiBancoApi: Login OK usuario=demo token=...`

**Bloque 2: `sesion.xml` de la app segura (paso 3).** Solo hay un dato, cifrado:
```xml
<string name="token_cifrado">5VcFnsyq9pQPn2WY+4ys...</string>
```
Es Base64(IV + AES-256-GCM). La llave vive en el Android Keystore, no se puede exportar y exige la huella.

**Bloque 3: jadx sobre la app vulnerable (paso 4):**
```
ApiClienteKt.java: public static final String API_KEY = "DEMO-FAKE-KEY-1234567890";
```
Las clases conservan sus nombres originales: `AlmacenSesion`, `ApiCliente`...

**Bloque 4: jadx sobre la app segura (paso 4):** `(nada) -> la app segura no filtra ningún secreto`.
R8 dejó las clases como `defpackage/a.java`, `b.java`, etc.

**Para comentar en la exposición:**
- `run-as` solo funciona con builds *debuggable*, como los de esta demo. En la vida real se
  obtiene lo mismo con un teléfono rooteado (`cat /data/data/<paquete>/shared_prefs/sesion.xml`),
  con un backup (`allowBackup="true"`) o con un análisis forense.
- **La ofuscación (R8) solo dificulta, no protege.** La defensa de verdad es **no tener
  secretos en el APK**: la sesión la emite el servidor.
- La huella de la app segura no es un simple "sí/no" que se pueda saltar: está ligada a la
  llave del Keystore mediante `CryptoObject`. Sin huella válida, el token **no se puede descifrar**.

---

## Demo 2: comunicación insegura

**Idea:** un atacante en la misma red (o que te convenció de instalar "un certificado")
se pone en medio (MITM) y lee el tráfico HTTPS.

### 2.1. Sin ataque: las dos apps funcionan

```bash
bash scripts/mitm-passthrough.sh     # terminal B: el TLS de api.mibanco.test pasa SIN interceptar
```
Inicia sesión en las dos apps. **Esperado:** ambas muestran el saldo, y en la terminal B **no
aparece nada** de `api.mibanco.test`, porque el proxy solo reenvía bytes cifrados.

### 2.2. Instalar la CA del atacante (mitmproxy) como certificado de usuario

Sigue [docs/instalar-ca-mitm.md](docs/instalar-ca-mitm.md). Resumen:
`bash scripts/enviar-ca-mitm.sh` y luego en Ajustes: *Security & privacy → More security &
privacy → Encryption & credentials → Install a certificate → CA certificate → Install anyway*.

### 2.3. Ataque: intercepción TLS

Detén la terminal B (Ctrl+C) y lánzala en modo ataque:
```bash
bash scripts/mitm-intercept.sh       # UI: http://127.0.0.1:8081
```
En cada app pulsa **Cerrar sesión** y vuelve a iniciar sesión.

**App VULNERABLE: el atacante lo ve todo.** En rojo, en la terminal B y en `out/interceptado.log`:
```
INTERCEPTADO POST /login  UA=MiBanco-Vulnerable/1.0
    X-API-Key: DEMO-FAKE-KEY-1234567890
    cuerpo: {"usuario":"demo","contrasena":"demo1234"}
    respuesta 200: {"expira_en":300,"token":"q1kY1Yfg_xpk..."}
INTERCEPTADO GET /balance
    Authorization: Bearer q1kY1Yfg_xpk...
    respuesta 200: {"cuenta":"0000-1234-5678-9012","saldo":2450380.75,"titular":"Ana Demo Pérez"}
```

**App SEGURA: la conexión falla y el atacante no ve nada.**
- En la app: *"Conexión rechazada: el certificado del servidor no es de confianza (posible
  intercepción / pinning fallido). SSLHandshakeException: ... Trust anchor for certification
  path not found."*
- En la terminal B: `FALLÓ la intercepción de api.mibanco.test: la app RECHAZÓ el certificado de mitmproxy`
- El backend (terminal A) **no recibe** ninguna petición de `MiBanco-Segura`.

### 2.4. Extra: el pinning por sí solo

¿Y si el atacante tuviera un certificado **válido**, emitido por una CA en la que la app sí
confía (por ejemplo, una CA comprometida)?

```bash
bash scripts/mitm-pinning.sh   # presenta un certificado de api.mibanco.test firmado por la CA de demo, pero con OTRA llave
```
- **Vulnerable:** cae de nuevo, porque confía en la CA y no tiene pinning.
- **Segura:** `SSLHandshakeException: Pin verification failed`. La cadena era válida, pero la
  llave no coincide con ninguno de los dos `<pin>`.

### 2.5. Volver a la normalidad

`bash scripts/mitm-passthrough.sh`: la app segura vuelve a funcionar **aunque la CA de
mitmproxy siga instalada**, porque no confía en CAs de usuario.

---

## Registrar una huella en el emulador

La app segura **no permite iniciar sesión sin biometría fuerte registrada**. En ese caso muestra
el aviso "Biometría requerida" con instrucciones y un botón para abrir Ajustes; no hay ningún
modo inseguro de respaldo.

**Automático:**
```bash
bash scripts/registrar-huella.sh
```
El script pone el PIN de demo `1234` si no hay bloqueo, abre el registro y toca el sensor por ti.
En el emulador solo tienes que escribir el PIN y pulsar **MORE** hasta **I AGREE**.

**Manual:**
1. *Settings → Security & privacy → Device unlock → Fingerprint Unlock* (Ajustes → Seguridad y
   privacidad → Desbloqueo del dispositivo → Desbloqueo con huella). Te pedirá crear un PIN.
2. En la pantalla "Touch the sensor", simula el dedo varias veces:
   - con `bash scripts/huella.sh` (equivale a `adb -e emu finger touch 1`), o
   - con el panel del emulador: **⋯ (Extended controls) → Fingerprint → Touch the sensor**.
3. Repite hasta ver **"Fingerprint added"**.

Cuando la app segura muestre el diálogo biométrico, ejecuta `bash scripts/huella.sh`.
Con `bash scripts/huella.sh 2` simulas un dedo **no registrado** ("Not recognized").

> Si registras una huella **nueva** después de iniciar sesión, la llave del Keystore se
> invalida (`setInvalidatedByBiometricEnrollment(true)`). La app lo detecta y pide iniciar
> sesión de nuevo.

---

## Tabla vulnerabilidad → defensa → referencia

| # | Vulnerabilidad (app `vulnerable/`) | Defensa (app `segura/`) | OWASP Mobile Top 10 2024 | MASVS v2 |
|---|---|---|---|---|
| 1 | Usuario, **contraseña** y token en `SharedPreferences` en claro (`sesion.xml`) | Solo el token, cifrado con **AES-256-GCM** usando una llave del **Android Keystore** (no exportable, IV aleatorio); la contraseña no se guarda nunca | M9 Insecure Data Storage; M1 Improper Credential Usage | MASVS-STORAGE-1, MASVS-CRYPTO-1/2 |
| 2 | Token y usuario escritos en **logcat** | Sin logs de datos sensibles; R8 elimina `Log.d/i/v` en release | M9 | MASVS-STORAGE-2 |
| 3 | **API key hardcodeada** (`DEMO-FAKE-KEY-…`) enviada en `X-API-Key` y visible con jadx | **Sin secretos en el APK**: el backend emite un token de sesión corto en `/login` | M1 Improper Credential Usage; M7 Insufficient Binary Protections | MASVS-STORAGE-1, MASVS-AUTH-1 |
| 4 | Sin R8/ofuscación | **R8** (minify, ofuscación, shrinkResources) en release; *solo dificulta* | M7 | MASVS-RESILIENCE-3 |
| 5 | `allowBackup="true"` | `allowBackup="false"` + `dataExtractionRules` / `fullBackupContent` que excluyen las preferencias | M9 | MASVS-STORAGE-2 |
| 6 | `debuggable` en debug (permite `run-as` y depurador) | Release **no debuggable** | M7 | MASVS-RESILIENCE-4 |
| 7 | `cleartextTrafficPermitted="true"` | `cleartextTrafficPermitted="false"` | M5 Insecure Communication | MASVS-NETWORK-1 |
| 8 | Confía en **CAs de usuario** (`<certificates src="user"/>`) → MITM con mitmproxy | Solo CAs del sistema; para `api.mibanco.test`, solo la CA de demo | M5 | MASVS-NETWORK-1 |
| 9 | Sin certificate pinning | `<pin-set>` con pin SPKI SHA-256 **principal y de respaldo**, con fecha de expiración | M5; M8 Security Misconfiguration | MASVS-NETWORK-2 |
| 10 | Saldo visible **sin autenticación** si hay token guardado; vuelve a iniciar sesión sola con la contraseña guardada | **BiometricPrompt + CryptoObject** (BIOMETRIC_STRONG): sin huella no se descifra el token ni se llama a `/balance`; si la sesión expira, hay que volver a iniciarla | M3 Insecure Authentication/Authorization | MASVS-AUTH-2 |
| 11 | n/a | La llave se invalida si se registran huellas nuevas | M3 | MASVS-AUTH-2 |

Configuración de red de cada app:
[vulnerable/.../network_security_config.xml](vulnerable/app/src/main/res/xml/network_security_config.xml) ·
[segura/.../network_security_config.xml](segura/app/src/main/res/xml/network_security_config.xml)
(este último lo **genera** `gen-certs.sh`).

---

## Problemas comunes

**Proxy y red**
- **"No se pudo resolver api.mibanco.test"** en la app: el emulador no está usando el proxy.
  Ejecuta `bash scripts/start-emulator.sh` (con el emulador ya abierto solo ajusta el proxy) y
  comprueba con `adb shell settings get global http_proxy`, que debe devolver `10.0.2.2:8080`.
- **"No se pudo conectar con el servidor"**: mitmproxy (terminal B) o el backend (terminal A)
  no están corriendo.
- **El emulador no tiene internet** (Chrome, Play): es normal si no hay ningún `mitm-*.sh`
  corriendo, porque todo pasa por el proxy. Para quitarlo: `bash scripts/start-emulator.sh --sin-proxy`.
- **No uses `emulator -http-proxy`**: con esa opción Android no recibe un proxy configurado y
  resuelve los nombres con el DNS del emulador, que **no lee el archivo hosts** de la PC.
  Por eso `start-emulator.sh` usa el proxy global de Android. Si aun así quieres probarlo:
  `--emu-http-proxy`.
- **Puerto 8080 u 8081 ocupado**: cierra el otro mitmweb (solo puede haber uno a la vez). En
  Windows: `taskkill /IM mitmweb.exe /F`.

**CA**
- **La app vulnerable falla con "certificado no es de confianza" en modo intercept**: la CA de
  mitmproxy no está instalada como certificado de usuario. Ver [docs/instalar-ca-mitm.md](docs/instalar-ca-mitm.md);
  compruébalo en *Trusted credentials → USER*.
- **Las dos apps fallan con el backend aunque no haya ataque**: regeneraste los certificados
  (`gen-certs.sh --force`) y no recompilaste. Ejecuta `bash scripts/instalar-apps.sh`.
- **La app segura deja de funcionar después de la fecha de `pines.txt`**: esa es la expiración
  del pin-set. Regenera los certificados y recompila.
- **curl en Windows: `CERT_TRUST_REVOCATION_STATUS_UNKNOWN`**: el curl de Git usa schannel;
  agrega `--ssl-no-revoke` (lo hace `verificar-backend.sh`).

**Hosts**
- `ping api.mibanco.test` en la PC debe responder desde `127.0.0.1`. Si no, revisa la línea del
  archivo hosts y ejecuta `ipconfig /flushdns` (Windows).
- Algunos antivirus bloquean la edición del archivo hosts: agrega la línea con el antivirus
  pausado.

**Huella**
- **La app segura muestra "Biometría requerida"**: no hay huella registrada. Ejecuta
  `bash scripts/registrar-huella.sh`.
- **El diálogo de huella no reacciona**: el comando debe ejecutarse **mientras** el diálogo está
  en pantalla (`bash scripts/huella.sh`). `adb devices` debe mostrar `emulator-5554`.
- **"Se detectó un cambio en las huellas registradas"**: registraste otra huella y la llave se
  invalidó (comportamiento esperado). Inicia sesión de nuevo.

**Compilación**
- **`SDK location not found`**: falta `local.properties`. Ejecuta `bash scripts/setup-env.sh`
  (lo genera) o exporta `ANDROID_HOME`.
- **`res/raw/demo_ca.pem` no existe**: ejecuta `bash scripts/gen-certs.sh`.
- **Error 403 de `repo.maven.apache.org`**: algunas redes reciben ese bloqueo. Los proyectos ya
  incluyen el espejo de Google de Maven Central.
- **El emulador va muy lento**: cierra Android Studio y otros programas pesados. El AVD usa
  2 GB de RAM; detén los daemons de Gradle con `./gradlew --stop`.

---

## Notas técnicas y decisiones

- **Por qué `compileSdk 35` y no las últimas librerías:** las últimas AndroidX estables (core
  1.19, Compose UI 1.12 / BOM 2026.09.00) y OkHttp 5.4+ exigen `compileSdk 36/37`. Como la demo
  pide `compileSdk`/`targetSdk 35`, se fijaron las versiones estables más recientes que
  declaran `minCompileSdk ≤ 35`. Para actualizar: sube `compileSdk`, instala la plataforma
  correspondiente y cambia el `libs.versions.toml`. `targetSdk` puede quedarse en 35.
- **build-tools:** AGP 9.5 exige build-tools 36.0.0 como mínimo, así que se usa la versión por
  defecto de AGP. 35.0.0 también queda instalado.
- **Kotlin integrado en AGP 9:** no se aplica `org.jetbrains.kotlin.android`, solo el plugin
  del compilador de Compose.
- **Pines:** `gen-certs.sh` calcula `base64(sha256(SubjectPublicKeyInfo))` de `server.key` y
  de `backup.key`, y escribe ambos en el `network_security_config.xml` de la app segura. La
  fecha `expiration` coincide con el vencimiento del certificado del servidor (397 días).
- **Builds de la demo:** se instala el build **debug** de ambas apps para poder usar `run-as`
  en la demo 1. `decompilar.sh` usa los builds **release**: sin R8 en la vulnerable y con R8 en
  la segura. El release de la segura **no es debuggable**.
- **No se usa `androidx.security:security-crypto`** (obsoleta). El cifrado usa
  `javax.crypto` + `AndroidKeyStore` directamente ([CifradorToken.kt](segura/app/src/main/java/com/demo/mibanco/segura/datos/CifradorToken.kt)).
