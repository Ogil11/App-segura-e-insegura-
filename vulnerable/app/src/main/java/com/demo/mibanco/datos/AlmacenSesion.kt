package com.demo.mibanco.datos

import android.content.Context
import androidx.core.content.edit

/**
 * Guarda la sesión del usuario.
 *
 * VULNERABLE: usa SharedPreferences en TEXTO PLANO (archivo
 * /data/data/com.demo.mibanco/shared_prefs/sesion.xml). Guarda usuario,
 * CONTRASEÑA y token sin cifrar. MODE_PRIVATE solo protege frente a otras apps;
 * no frente a root, "adb shell run-as" (build debuggable), backups (allowBackup)
 * ni análisis forense (MASVS-STORAGE-1, OWASP M9 Insecure Data Storage).
 */
class AlmacenSesion(contexto: Context) {

    // VULNERABLE: archivo "sesion" sin cifrado (MASVS-STORAGE-1, OWASP M9)
    private val prefs = contexto.getSharedPreferences("sesion", Context.MODE_PRIVATE)

    val usuario: String? get() = prefs.getString("usuario", null)

    // VULNERABLE: guardar la contraseña nunca es necesario: con un token basta
    // (MASVS-STORAGE-1, OWASP M1/M9).
    val contrasena: String? get() = prefs.getString("contrasena", null)

    val token: String? get() = prefs.getString("token", null)

    fun guardar(usuario: String, contrasena: String, token: String) {
        // VULNERABLE: todo en claro (MASVS-STORAGE-1, OWASP M9)
        prefs.edit(commit = true) {
            putString("usuario", usuario)
            putString("contrasena", contrasena)
            putString("token", token)
        }
    }

    fun actualizarToken(token: String) = prefs.edit(commit = true) { putString("token", token) }

    fun borrar() = prefs.edit(commit = true) { clear() }
}
