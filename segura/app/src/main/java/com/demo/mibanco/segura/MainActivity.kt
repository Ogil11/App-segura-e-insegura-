package com.demo.mibanco.segura

import android.os.Bundle
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.demo.mibanco.segura.datos.AlmacenSesion
import com.demo.mibanco.segura.datos.ApiCliente
import com.demo.mibanco.segura.datos.Biometria
import com.demo.mibanco.segura.datos.CifradorToken
import com.demo.mibanco.segura.datos.Cuenta
import com.demo.mibanco.segura.datos.ErrorApi
import com.demo.mibanco.segura.datos.ErrorBiometria
import com.demo.mibanco.segura.datos.describirError
import com.demo.mibanco.segura.ui.PantallaLogin
import com.demo.mibanco.segura.ui.PantallaSaldo
import com.demo.mibanco.segura.ui.TemaMiBanco
import kotlinx.coroutines.launch

/** FragmentActivity (y no ComponentActivity) porque BiometricPrompt la necesita. */
class MainActivity : FragmentActivity() {

    // Se vuelve a consultar al regresar de Ajustes (p. ej. tras registrar la huella).
    private val estadoBiometria = mutableIntStateOf(BiometricManager.BIOMETRIC_SUCCESS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val almacen = AlmacenSesion(applicationContext)
        val cifrador = CifradorToken()
        val api = ApiCliente()
        estadoBiometria.intValue = Biometria.estado(this)
        setContent {
            TemaMiBanco {
                AppMiBanco(this, almacen, cifrador, api, estadoBiometria.intValue)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        estadoBiometria.intValue = Biometria.estado(this)
    }
}

@Composable
private fun AppMiBanco(
    actividad: FragmentActivity,
    almacen: AlmacenSesion,
    cifrador: CifradorToken,
    api: ApiCliente,
    estadoBiometria: Int,
) {
    // SEGURO (e): aunque exista una sesión guardada, la pantalla de saldo arranca
    // BLOQUEADA; el token solo se descifra tras autenticarse con huella (MASVS-AUTH-2).
    var enSaldo by remember { mutableStateOf(almacen.haySesion) }
    // Token en claro SOLO en memoria y solo después de autenticarse (nunca en disco).
    var token by remember { mutableStateOf<String?>(null) }
    var cuenta by remember { mutableStateOf<Cuenta?>(null) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var intento by remember { mutableIntStateOf(0) }
    var autoDesbloqueoHecho by remember { mutableStateOf(false) }
    val alcance = rememberCoroutineScope()

    fun cerrarSesion(mensaje: String? = null) {
        almacen.borrar()
        token = null; cuenta = null
        error = mensaje
        enSaldo = false
    }

    /** Desbloquea con huella: descifra el token y consulta el saldo. */
    fun desbloquear() {
        alcance.launch {
            cargando = true; error = null
            try {
                val iv = almacen.iv() ?: throw IllegalStateException("No hay sesión guardada")
                val cipher = cifrador.cipherParaDescifrar(iv)
                // SEGURO (e): sin huella válida el Keystore no permite usar la llave.
                val autorizado = Biometria.autenticar(
                    actividad, "Ver tu saldo", "Confirma tu identidad con tu huella", cipher
                )
                val claro = almacen.descifrar(autorizado)
                token = claro
                cuenta = api.balance(claro)
            } catch (e: KeyPermanentlyInvalidatedException) {
                // SEGURO: se registró una huella nueva -> la llave ya no sirve.
                cifrador.borrarLlave()
                cerrarSesion("Se detectó un cambio en las huellas registradas. Por seguridad, " +
                    "la sesión guardada se invalidó: inicia sesión de nuevo.")
            } catch (e: ErrorBiometria) {
                error = if (e.cancelado) "Autenticación cancelada. Tu saldo sigue protegido."
                else "Biometría: ${e.message}"
            } catch (e: ErrorApi) {
                if (e.codigo == 401) {
                    // SEGURO: no hay contraseña guardada para "re-loguear" a escondidas.
                    cerrarSesion("Tu sesión expiró. Inicia sesión de nuevo.")
                } else error = describirError(e)
            } catch (e: Exception) {
                error = describirError(e)
            } finally {
                cargando = false
            }
        }
    }

    if (!enSaldo) {
        val explicacion = Biometria.explicacion(estadoBiometria)
        PantallaLogin(
            cargando = cargando,
            error = error,
            // SEGURO: sin biometría fuerte registrada NO se permite iniciar sesión
            // (no se cae a un modo inseguro).
            habilitado = explicacion == null,
            aviso = explicacion?.let { texto ->
                { AvisoBiometria(texto) { actividad.startActivity(Biometria.intentRegistrar()) } }
            },
            onEntrar = { usuario, contrasena ->
                alcance.launch {
                    cargando = true; error = null
                    try {
                        val nuevo = api.login(usuario, contrasena)
                        val cipher = try {
                            cifrador.cipherParaCifrar()
                        } catch (e: KeyPermanentlyInvalidatedException) {
                            cifrador.borrarLlave(); cifrador.cipherParaCifrar()
                        }
                        // SEGURO (a/e): la llave exige huella también para cifrar.
                        val autorizado = Biometria.autenticar(
                            actividad, "Proteger tu sesión",
                            "Confirma con tu huella para guardar la sesión cifrada", cipher
                        )
                        // SEGURO (a): se guarda SOLO el token, cifrado con AES-256-GCM.
                        almacen.guardar(autorizado, nuevo)
                        token = nuevo
                        autoDesbloqueoHecho = true   // ya se autenticó: no volver a pedir huella
                        enSaldo = true
                        intento++
                    } catch (e: ErrorBiometria) {
                        error = if (e.cancelado) "Sin confirmar tu huella no se guarda la sesión. Inténtalo de nuevo."
                        else "Biometría: ${e.message}"
                    } catch (e: Exception) {
                        error = describirError(e)
                    } finally {
                        cargando = false
                    }
                }
            }
        )
    } else {
        // Recién logueado: el token ya está en memoria -> consultar saldo.
        LaunchedEffect(intento) {
            val t = token ?: return@LaunchedEffect
            cargando = true; error = null
            try {
                cuenta = api.balance(t)
            } catch (e: ErrorApi) {
                if (e.codigo == 401) cerrarSesion("Tu sesión expiró. Inicia sesión de nuevo.")
                else error = describirError(e)
            } catch (e: Exception) {
                error = describirError(e)
            } finally {
                cargando = false
            }
        }
        // Al abrir la app con sesión guardada se pide la huella automáticamente una vez.
        LaunchedEffect(Unit) {
            if (token == null && !autoDesbloqueoHecho) {
                autoDesbloqueoHecho = true
                desbloquear()
            }
        }
        PantallaSaldo(
            cuenta = cuenta,
            cargando = cargando,
            error = error,
            bloqueado = token == null,
            onDesbloquear = { desbloquear() },
            onReintentar = { if (token == null) desbloquear() else intento++ },
            onCerrarSesion = { cerrarSesion() },
        )
    }
}

@Composable
private fun AvisoBiometria(texto: String, onRegistrar: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E5)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Biometría requerida", color = Color(0xFF8A4B00))
            Text(texto, color = Color(0xFF5D3A00))
            TextButton(onClick = onRegistrar) { Text("Abrir ajustes para registrar huella") }
        }
    }
}
