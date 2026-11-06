package com.demo.mibanco.datos

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// VULNERABLE: API key hardcodeada en el código. Cualquiera la extrae del APK con
// jadx/apktool en segundos; además no hay R8/ofuscación (MASVS-STORAGE-1,
// MASVS-RESILIENCE-3, OWASP M1 Improper Credential Usage).
// Es una clave FALSA de demostración: no da acceso a nada real.
const val API_KEY = "DEMO-FAKE-KEY-1234567890"

const val URL_BASE = "https://api.mibanco.test:8443"

private const val TAG = "MiBancoApi"

/** Datos de la cuenta que devuelve GET /balance (todo ficticio). */
data class Cuenta(val titular: String, val cuenta: String, val saldo: Double, val moneda: String)

/** Error HTTP del backend (p. ej. 401 credenciales o token inválido). */
class ErrorApi(val codigo: Int, mensaje: String) : Exception(mensaje)

/** Cliente HTTP mínimo con OkHttp (sin Retrofit, para que se lea fácil en la exposición). */
class ApiCliente {

    private val json = "application/json; charset=utf-8".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { cadena ->
            val peticion = cadena.request().newBuilder()
                // VULNERABLE: el "secreto" viaja en cada petición; si el TLS se
                // intercepta (CA de usuario), el atacante lo ve en claro (OWASP M1, M5).
                .header("X-API-Key", API_KEY)
                .header("User-Agent", "MiBanco-Vulnerable/1.0")
                .build()
            cadena.proceed(peticion)
        }
        .build()

    /** POST /login -> token de sesión. */
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
            val token = JSONObject(texto).getString("token")
            // VULNERABLE: el token (y el usuario) se escriben en logcat, legible con
            // "adb logcat" o por apps con permisos de lectura de logs (MASVS-STORAGE-2, OWASP M9).
            Log.d(TAG, "Login OK usuario=$usuario token=$token")
            token
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
