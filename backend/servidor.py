"""
Backend de PRUEBA de MiBanco (ficticio) para la demo de seguridad móvil.

- HTTPS en el puerto 8443, host de demo api.mibanco.test (resuelto a 127.0.0.1
  mediante el archivo hosts de la PC).
- POST /login   -> {"token": "...", "expira_en": 300}
- GET  /balance -> exige "Authorization: Bearer <token>"
- Acepta (pero NO exige) la cabecera X-API-Key que envía la app vulnerable.
- Registra en consola cada petición para ver qué llega al servidor.

TODO lo de aquí es FALSO y solo de demostración: el usuario, la contraseña,
los datos de la cuenta y los tokens no tienen ningún valor real.
"""

import json
import logging
import os
import secrets
import sys
import time
from pathlib import Path

from flask import Flask, g, jsonify, request

RAIZ = Path(__file__).resolve().parent.parent
CERT = RAIZ / "certs" / "server.crt"
LLAVE = RAIZ / "certs" / "server.key"

HOST = os.environ.get("MIBANCO_HOST", "127.0.0.1")
PUERTO = int(os.environ.get("MIBANCO_PUERTO", "8443"))

# Credenciales FALSAS de demostración (no son secretos reales).
USUARIO_DEMO = "demo"
CONTRASENA_DEMO = "demo1234"

# Duración corta de la sesión: 5 minutos.
TTL_TOKEN_SEG = 300

# token -> (usuario, instante de expiración). En memoria: se pierde al reiniciar.
SESIONES: dict[str, tuple[str, float]] = {}

CUENTA_DEMO = {
    "titular": "Ana Demo Pérez",
    "cuenta": "0000-1234-5678-9012",   # número de cuenta FICTICIO
    "saldo": 2_450_380.75,             # saldo FICTICIO
    "moneda": "COP",
}

app = Flask(__name__)
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(message)s", datefmt="%H:%M:%S")
log = logging.getLogger("mibanco")
logging.getLogger("werkzeug").setLevel(logging.WARNING)  # evita doble registro


def _recortar(valor: str | None, n: int = 12) -> str:
    """Muestra solo el inicio de un valor sensible en el registro."""
    if not valor:
        return "-"
    return valor if len(valor) <= n else valor[:n] + "…"


@app.before_request
def registrar_peticion():
    g.inicio = time.time()
    cuerpo = ""
    if request.is_json:
        datos = dict(request.get_json(silent=True) or {})
        if "contrasena" in datos:
            datos["contrasena"] = "***"  # el servidor no imprime contraseñas
        cuerpo = json.dumps(datos, ensure_ascii=False)
    log.info(
        "--> %s %s | Authorization=%s | X-API-Key=%s | User-Agent=%s %s",
        request.method,
        request.path,
        _recortar(request.headers.get("Authorization"), 20),
        request.headers.get("X-API-Key", "-"),
        request.headers.get("User-Agent", "-"),
        cuerpo,
    )


@app.after_request
def registrar_respuesta(resp):
    log.info("<-- %s %s %d (%.0f ms)", request.method, request.path,
             resp.status_code, (time.time() - g.inicio) * 1000)
    return resp


@app.post("/login")
def login():
    datos = request.get_json(silent=True) or {}
    usuario = str(datos.get("usuario", ""))
    contrasena = str(datos.get("contrasena", ""))

    # Comparación en tiempo constante para no filtrar información por tiempos.
    valido = secrets.compare_digest(usuario, USUARIO_DEMO) & secrets.compare_digest(
        contrasena, CONTRASENA_DEMO)
    if not valido:
        return jsonify(error="Usuario o contraseña incorrectos"), 401

    # Token de sesión aleatorio (256 bits) con expiración corta.
    token = secrets.token_urlsafe(32)
    SESIONES[token] = (usuario, time.time() + TTL_TOKEN_SEG)
    return jsonify(token=token, expira_en=TTL_TOKEN_SEG)


@app.get("/balance")
def balance():
    cabecera = request.headers.get("Authorization", "")
    if not cabecera.startswith("Bearer "):
        return jsonify(error="Falta la cabecera Authorization: Bearer <token>"), 401

    token = cabecera.removeprefix("Bearer ").strip()
    sesion = SESIONES.get(token)
    if sesion is None:
        return jsonify(error="Token inválido"), 401
    if time.time() > sesion[1]:
        SESIONES.pop(token, None)
        return jsonify(error="Sesión expirada, vuelve a iniciar sesión"), 401

    return jsonify(CUENTA_DEMO)


@app.get("/")
def salud():
    return jsonify(estado="ok", servicio="MiBanco backend de demo")


def main():
    if not CERT.exists() or not LLAVE.exists():
        print(f"No se encontraron {CERT} / {LLAVE}.\n"
              "Genera los certificados primero:  bash scripts/gen-certs.sh", file=sys.stderr)
        sys.exit(1)
    log.info("MiBanco backend escuchando en https://api.mibanco.test:%d (bind %s)", PUERTO, HOST)
    log.info("Usuario demo: %s / %s (credenciales FALSAS de demostración)",
             USUARIO_DEMO, CONTRASENA_DEMO)
    app.run(host=HOST, port=PUERTO, ssl_context=(str(CERT), str(LLAVE)), threaded=True)


if __name__ == "__main__":
    main()
