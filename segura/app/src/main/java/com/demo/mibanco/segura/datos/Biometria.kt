package com.demo.mibanco.segura.datos

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.crypto.Cipher
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Error o cancelación devuelta por BiometricPrompt. */
class ErrorBiometria(val codigo: Int, mensaje: String) : Exception(mensaje) {
    val cancelado: Boolean
        get() = codigo == BiometricPrompt.ERROR_USER_CANCELED ||
            codigo == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
            codigo == BiometricPrompt.ERROR_CANCELED
}

/**
 * Autenticación biométrica ligada a criptografía.
 *
 * SEGURO: BiometricPrompt con CryptoObject: la huella no es un simple "sí/no"
 * que se pueda saltar con un hook (Frida) -- desbloquea el Cipher dentro del
 * Keystore. Sin huella válida, el token simplemente no se puede descifrar
 * (MASVS-AUTH-2, OWASP M3).
 */
object Biometria {

    /** BiometricManager.BIOMETRIC_SUCCESS si hay biometría fuerte registrada. */
    fun estado(contexto: Context): Int =
        BiometricManager.from(contexto).canAuthenticate(BIOMETRIC_STRONG)

    /**
     * Explicación clara cuando NO se puede usar biometría.
     * SEGURO: no se cae silenciosamente a un modo sin protección.
     */
    fun explicacion(estado: Int): String? = when (estado) {
        BiometricManager.BIOMETRIC_SUCCESS -> null
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
            "Esta app exige huella para proteger tu sesión y no hay ninguna registrada.\n" +
                "Ve a Ajustes > Seguridad y privacidad > Desbloqueo del dispositivo > " +
                "Desbloqueo con huella (primero se pide crear un PIN) y registra una huella.\n" +
                "En el emulador, cuando pida tocar el sensor ejecuta en la PC: " +
                "adb -e emu finger touch 1"
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
            "Este dispositivo no tiene sensor biométrico. La app segura no puede " +
                "proteger la sesión y por eso no permite iniciar sesión."
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
            "El sensor biométrico no está disponible en este momento. Inténtalo de nuevo."
        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
            "El sistema necesita una actualización de seguridad para usar la biometría."
        else -> "No se puede usar biometría fuerte en este dispositivo (código $estado)."
    }

    /** Intent para abrir el registro de huella en Ajustes. */
    fun intentRegistrar(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL)
                .putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, BIOMETRIC_STRONG)
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }

    /** Muestra BiometricPrompt y devuelve el Cipher ya autorizado por el Keystore. */
    suspend fun autenticar(
        actividad: FragmentActivity,
        titulo: String,
        subtitulo: String,
        cipher: Cipher,
    ): Cipher = suspendCancellableCoroutine { cont ->
        val prompt = BiometricPrompt(
            actividad,
            ContextCompat.getMainExecutor(actividad),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(resultado: BiometricPrompt.AuthenticationResult) {
                    val autorizado = resultado.cryptoObject?.cipher
                    if (!cont.isActive) return
                    if (autorizado != null) cont.resume(autorizado)
                    else cont.resumeWithException(ErrorBiometria(-1, "Autenticación sin CryptoObject"))
                }

                override fun onAuthenticationError(codigo: Int, mensaje: CharSequence) {
                    if (cont.isActive) cont.resumeWithException(ErrorBiometria(codigo, mensaje.toString()))
                }
                // onAuthenticationFailed: huella no reconocida; el diálogo sigue abierto.
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(titulo)
            .setSubtitle(subtitulo)
            .setNegativeButtonText("Cancelar")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)   // SEGURO: solo biometría clase 3
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        cont.invokeOnCancellation { prompt.cancelAuthentication() }
    }
}
