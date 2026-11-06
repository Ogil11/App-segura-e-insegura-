# Instalar la CA de mitmproxy en el emulador (certificado de USUARIO)

Este paso prepara la **demo 2 (comunicación insegura)**. Simula lo que un atacante
consigue cuando engaña a la víctima para que instale un "certificado de seguridad"
(perfil de la empresa, wifi de un hotel, falso soporte técnico) o cuando tiene el
teléfono desbloqueado unos minutos.

> La CA de mitmproxy se genera sola la primera vez que arrancas mitmproxy, en
> `~/.mitmproxy/` de tu PC (en Windows: `C:\Users\<tu usuario>\.mitmproxy\`).
> Solo sirve para este laboratorio. **Desinstálala al terminar la exposición.**

## 1. Copiar el certificado al emulador

Con el emulador encendido:

```bash
bash scripts/enviar-ca-mitm.sh
```

El script copia `mitmproxy-ca-cert.cer` a la carpeta **Descargas** del emulador
(`/sdcard/Download/`) e imprime su huella SHA-256.

## 2. Instalarlo desde Ajustes

El emulador de la demo está en inglés. Entre paréntesis va el nombre en español.

1. Abre **Settings** (Ajustes) y entra en **Security & privacy** (Seguridad y privacidad).
   *Verás tarjetas de estado como "Device unlock" y "App security".*
2. Baja hasta el final y entra en **More security & privacy** (Más ajustes de seguridad y privacidad).
3. Busca la sección **Security** y entra en **Encryption & credentials**
   (Cifrado y credenciales).
   *La pantalla muestra "Encrypt phone: Encrypted", "Trusted credentials", "User credentials"
   e "Install a certificate".*
4. Pulsa **Install a certificate** (Instalar un certificado). Aparecen tres
   opciones: *CA certificate*, *VPN & app user certificate* y *Wi‑Fi certificate*.
5. Pulsa **CA certificate** (Certificado de CA).
6. Android muestra una advertencia en rojo: **"Your data won't be private"**
   ("Tus datos no serán privados"), que explica que el dueño de la CA podría ver
   contraseñas y datos de tarjetas. **Este es el momento clave de la exposición:**
   el sistema avisa al usuario, pero el usuario puede aceptar. Pulsa
   **INSTALL ANYWAY** (Instalar de todas formas).
7. Confirma tu identidad con el **PIN 1234** o con la huella
   (`bash scripts/huella.sh`).
8. Se abre el selector de archivos en "Recent" (Recientes). Toca el menú ☰
   (*Show roots*), entra en **Downloads** (Descargas) y elige **mitmproxy-ca-cert.cer**.
9. Vuelves a la lista de tipos de certificado y aparece un aviso breve de
   "CA certificate installed".

**Comprobación:** en *Encryption & credentials → Trusted credentials* (Credenciales
de confianza), en la pestaña **USER** (Usuario), aparece **mitmproxy**.

> Atajo: `bash scripts/enviar-ca-mitm.sh --ajustes` intenta abrir directamente
> la pantalla de seguridad.

## 3. ¿Por qué la app vulnerable cae y la segura no?

Desde **Android 7.0 (API 24)**, las apps que apuntan a esa versión o a una posterior
**no confían por defecto en las CAs instaladas por el usuario**: solo en las CAs del
sistema, que vienen de fábrica en la ROM. Una app solo acepta CAs de usuario si lo
pide de forma expresa en su `network_security_config.xml`.

| | App VULNERABLE | App SEGURA |
|---|---|---|
| `<certificates src="user"/>` | **Sí** (la añade a propósito) | No |
| CAs en las que confía | sistema + **usuario** + CA demo | sistema; y para `api.mibanco.test` solo la CA demo |
| Certificate pinning (`<pin-set>`) | No | Sí: pin principal + pin de respaldo |
| HTTP en claro | Permitido | Prohibido |
| Resultado con mitmproxy interceptando | mitmproxy ve contraseña, token, API key y saldo | `SSLHandshakeException: Trust anchor for certification path not found` |

Y si el atacante consiguiera un certificado **válido** firmado por una CA en la que
la app sí confía (CA comprometida o mal emitida), el **pinning** sigue protegiendo:
`bash scripts/mitm-pinning.sh` lo demuestra y la app segura responde con
`SSLHandshakeException: Pin verification failed`.

## 4. Desinstalar la CA al terminar

*Encryption & credentials → Trusted credentials → USER → mitmproxy → REMOVE*
(Credenciales de confianza → Usuario → mitmproxy → Quitar), o
*Encryption & credentials → Clear credentials* (Borrar credenciales).
