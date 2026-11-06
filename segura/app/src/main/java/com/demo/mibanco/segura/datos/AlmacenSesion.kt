package com.demo.mibanco.segura.datos

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import javax.crypto.Cipher

/**
 * Guarda SOLO el token de sesión, cifrado.
 *
 * SEGURO: en shared_prefs/sesion.xml solo hay Base64(iv + texto cifrado AES-GCM).
 * Sin la llave del Keystore (no exportable y protegida por biometría) ese valor
 * no sirve de nada aunque se extraiga con run-as, root o un backup
 * (MASVS-STORAGE-1, OWASP M9).
 * SEGURO: la contraseña NO se guarda en ningún sitio (OWASP M1).
 */
class AlmacenSesion(contexto: Context) {

    private val prefs = contexto.getSharedPreferences("sesion", Context.MODE_PRIVATE)

    val haySesion: Boolean get() = prefs.contains(CLAVE)

    /** Cifra y guarda el token. [cipher] debe venir ya autenticado por BiometricPrompt. */
    fun guardar(cipher: Cipher, token: String) {
        val cifrado = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val blob = cipher.iv + cifrado          // iv (12 bytes) + texto cifrado con tag GCM
        prefs.edit(commit = true) { putString(CLAVE, Base64.encodeToString(blob, Base64.NO_WRAP)) }
    }

    /** IV guardado, necesario para preparar el Cipher de descifrado. */
    fun iv(): ByteArray? = blob()?.copyOfRange(0, CifradorToken.IV_BYTES)

    /** Descifra el token. [cipher] debe venir ya autenticado por BiometricPrompt. */
    fun descifrar(cipher: Cipher): String {
        val b = blob() ?: throw IllegalStateException("No hay sesión guardada")
        val claro = cipher.doFinal(b.copyOfRange(CifradorToken.IV_BYTES, b.size))
        return String(claro, Charsets.UTF_8)
    }

    fun borrar() = prefs.edit(commit = true) { clear() }

    private fun blob(): ByteArray? =
        prefs.getString(CLAVE, null)?.let { Base64.decode(it, Base64.NO_WRAP) }

    private companion object {
        const val CLAVE = "token_cifrado"
    }
}
