package com.demo.mibanco.segura.datos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// SEGURO: solo HTTPS. Aunque alguien escribiera "http://", el
// network_security_config prohíbe el tráfico en claro (MASVS-NETWORK-1).
const val URL_BASE = "https://api.mibanco.test:8443"

/** Datos de la cuenta que devuelve GET /balance (todo ficticio). */
data class Cuenta(val titular: String, val cuenta: String, val saldo: Double, val moneda: String)

/** Error HTTP del backend (p. ej. 401 credenciales o token inválido/expirado). */
class ErrorApi(val codigo: Int, mensaje: String) : Exception(mensaje)

/**
 * Cliente HTTP mínimo con OkHttp.
 *
 * SEGURO: NO hay API key ni ningún secreto en el APK: la única credencial es el
 * token de sesión de corta duración que emite el backend en /login
 * (MASVS-STORAGE-1, MASVS-AUTH-1; OWASP M1).
 *
 * SEGURO: la validación TLS y el certificate pinning los aplica la plataforma
 * según res/xml/network_security_config.xml (OkHttp usa el TrustManager del
 * sistema, que respeta esa configuración) (MASVS-NETWORK-1, MASVS-NETWORK-2).
 */
class ApiCliente {

    private val json = "application/json; charset=utf-8".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { cadena ->
            cadena.proceed(
                cadena.request().newBuilder().header("User-Agent", "MiBanco-Segura/1.0").build()
            )
        }
        .build()

    /** POST /login -> token de sesión. La contraseña solo viaja aquí (por TLS) y no se guarda. */
    suspend fun login(usuario: String, contrasena: String): String = withContext(Dispatchers.IO) {
        val cuerpo = JSONObject()
            .put("usuario", usuario)
            .put("contrasena", contrasena)
            .toString()
            .toRequestBody(json)
        val peticion = Request.Builder().url("$URL_BASE/login").post(cuerpo).build()
        http.newCall(peticion).execute().use { r ->
            val texto = r.body.string()
            if (!r.isSuccessful) throw ErrorApi(r.code, mensajeDeError(texto, r.code))
            // SEGURO: no se escribe el token en logcat (MASVS-STORAGE-2).
            JSONObject(texto).getString("token")
        }
    }

    /** GET /balance con Authorization: Bearer <token>. */
    suspend fun balance(token: String): Cuenta = withContext(Dispatchers.IO) {
        val peticion = Request.Builder()
            .url("$URL_BASE/balance")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        http.newCall(peticion).execute().use { r ->
            val texto = r.body.string()
            if (!r.isSuccessful) throw ErrorApi(r.code, mensajeDeError(texto, r.code))
            val o = JSONObject(texto)
            Cuenta(
                titular = o.getString("titular"),
                cuenta = o.getString("cuenta"),
                saldo = o.getDouble("saldo"),
                moneda = o.optString("moneda", "COP"),
            )
        }
    }

    private fun mensajeDeError(texto: String, codigo: Int): String =
        runCatching { JSONObject(texto).getString("error") }.getOrDefault("Error HTTP $codigo")
}
