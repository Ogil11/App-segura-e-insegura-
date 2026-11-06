package com.demo.mibanco.segura.datos

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Gestiona la llave AES-256 del Android Keystore que protege el token.
 *
 * SEGURO: se usa javax.crypto + AndroidKeyStore directamente (NO
 * androidx.security:security-crypto, que está obsoleta) (MASVS-CRYPTO-1/2).
 *
 * - La llave se genera DENTRO del Keystore (TEE/hardware si existe) y no es
 *   exportable: ni la app ni un atacante con root pueden leer sus bytes.
 * - AES/GCM/NoPadding: cifrado autenticado (detecta manipulación del dato).
 * - IV aleatorio por cifrado: lo genera el Keystore (setRandomizedEncryptionRequired)
 *   y se guarda junto al texto cifrado.
 * - setUserAuthenticationRequired(true) + BIOMETRIC_STRONG con timeout 0: CADA uso
 *   de la llave exige una autenticación biométrica fuerte (BiometricPrompt con
 *   CryptoObject) (MASVS-AUTH-2, MASVS-STORAGE-1).
 * - setInvalidatedByBiometricEnrollment(true): si se registra una huella nueva,
 *   la llave queda inservible (KeyPermanentlyInvalidatedException).
 */
class CifradorToken {

    private val keyStore: KeyStore = KeyStore.getInstance(PROVEEDOR).apply { load(null) }

    private fun obtenerLlave(): SecretKey =
        (keyStore.getKey(ALIAS, null) as? SecretKey) ?: crearLlave()

    private fun crearLlave(): SecretKey {
        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVEEDOR)
        val spec = KeyGenParameterSpec.Builder(
            ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(256)                                            // SEGURO: AES-256 (MASVS-CRYPTO-2)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)                // SEGURO: modo autenticado
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)                      // SEGURO: IV aleatorio obligatorio
            .setUserAuthenticationRequired(true)                        // SEGURO: exige autenticación (MASVS-AUTH-2)
            .setInvalidatedByBiometricEnrollment(true)                  // SEGURO: huella nueva => llave inválida
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // SEGURO: timeout 0 = autenticación por CADA operación, solo biometría fuerte (clase 3)
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                } else {
                    // Android 9-10: -1 = autenticación por operación con biometría
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(-1)
                }
            }
            .build()
        generador.init(spec)
        return generador.generateKey()
    }

    /**
     * Cipher listo para CIFRAR; hay que pasarlo por BiometricPrompt antes de usarlo.
     * Puede lanzar KeyPermanentlyInvalidatedException (ver [borrarLlave]).
     */
    fun cipherParaCifrar(): Cipher =
        Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.ENCRYPT_MODE, obtenerLlave()) }

    /** Cipher listo para DESCIFRAR con el IV guardado; requiere BiometricPrompt. */
    fun cipherParaDescifrar(iv: ByteArray): Cipher =
        Cipher.getInstance(TRANSFORMACION).apply {
            init(Cipher.DECRYPT_MODE, obtenerLlave(), GCMParameterSpec(TAG_BITS, iv))
        }

    /** Elimina la llave (p. ej. tras KeyPermanentlyInvalidatedException). */
    fun borrarLlave() {
        if (keyStore.containsAlias(ALIAS)) keyStore.deleteEntry(ALIAS)
    }

    companion object {
        private const val PROVEEDOR = "AndroidKeyStore"
        private const val ALIAS = "mibanco_token_aes"
        private const val TRANSFORMACION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}
