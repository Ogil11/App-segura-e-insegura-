package com.demo.mibanco.segura.datos

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/** Convierte una excepción de red en un mensaje claro para mostrar en la demo. */
fun describirError(e: Throwable): String = when (e) {
    is ErrorApi -> e.message ?: "Error del servidor (${e.codigo})"
    is SSLPeerUnverifiedException, is SSLHandshakeException ->
        "Conexión rechazada: el certificado del servidor no es de confianza " +
            "(posible intercepción / pinning fallido).\n${e.javaClass.simpleName}: ${e.message}"
    is UnknownHostException -> "No se pudo resolver api.mibanco.test. ¿Está el emulador usando el proxy y la PC tiene la entrada en hosts?"
    is ConnectException -> "No se pudo conectar con el servidor. ¿Está corriendo el backend / mitmproxy?"
    is SocketTimeoutException -> "El servidor tardó demasiado en responder."
    else -> "${e.javaClass.simpleName}: ${e.message}"
}
