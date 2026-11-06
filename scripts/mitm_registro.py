"""
Addon de mitmproxy para la demo: imprime en la terminal lo que el atacante
ve al interceptar el tráfico de api.mibanco.test (y lo guarda en
out/interceptado.log). Solo para el laboratorio de la exposición.

Uso (lo cargan mitm-intercept.sh / mitm-passthrough.sh):
    mitmweb -s scripts/mitm_registro.py
"""

from datetime import datetime
from pathlib import Path

from mitmproxy import http, tls

HOST = "api.mibanco.test"
LOG = Path(__file__).resolve().parent.parent / "out" / "interceptado.log"


def _escribir(texto: str) -> None:
    linea = f"[{datetime.now():%H:%M:%S}] {texto}"
    print(f"\033[1;31m{linea}\033[0m", flush=True)
    LOG.parent.mkdir(exist_ok=True)
    with LOG.open("a", encoding="utf-8") as f:
        f.write(linea + "\n")


def request(flow: http.HTTPFlow) -> None:
    if flow.request.pretty_host != HOST:
        return
    r = flow.request
    _escribir(f"INTERCEPTADO {r.method} {r.path}  UA={r.headers.get('User-Agent', '-')}")
    for cabecera in ("Authorization", "X-API-Key"):
        if cabecera in r.headers:
            _escribir(f"    {cabecera}: {r.headers[cabecera]}")
    if r.content:
        _escribir(f"    cuerpo: {r.get_text(strict=False)}")


def response(flow: http.HTTPFlow) -> None:
    if flow.request.pretty_host != HOST or flow.response is None:
        return
    _escribir(f"    respuesta {flow.response.status_code}: {flow.response.get_text(strict=False)}")


def tls_failed_client(data: tls.TlsData) -> None:
    """El cliente (la app) rechazó el certificado falso de mitmproxy."""
    sni = data.conn.sni or "?"
    if sni == HOST:
        _escribir(f"FALLÓ la intercepción de {sni}: la app RECHAZÓ el certificado de mitmproxy "
                  f"(CA de usuario no confiable / pinning). No se ve nada.")
