package com.demo.mibanco

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.demo.mibanco.datos.AlmacenSesion
import com.demo.mibanco.datos.ApiCliente
import com.demo.mibanco.datos.Cuenta
import com.demo.mibanco.datos.ErrorApi
import com.demo.mibanco.datos.describirError
import com.demo.mibanco.ui.PantallaLogin
import com.demo.mibanco.ui.PantallaSaldo
import com.demo.mibanco.ui.TemaMiBanco
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val almacen = AlmacenSesion(applicationContext)
        val api = ApiCliente()
        setContent { TemaMiBanco { AppMiBanco(almacen, api) } }
    }
}

@Composable
private fun AppMiBanco(almacen: AlmacenSesion, api: ApiCliente) {
    // VULNERABLE (e): si hay un token guardado se entra DIRECTO a Saldo, sin pedir
    // biometría ni PIN. Quien tenga el teléfono desbloqueado ve el saldo
    // (MASVS-AUTH-2, OWASP M3 Insecure Authentication/Authorization).
    var enSaldo by rememberSaveable { mutableStateOf(almacen.token != null) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cuenta by remember { mutableStateOf<Cuenta?>(null) }
    var intento by remember { mutableStateOf(0) }
    val alcance = rememberCoroutineScope()

    if (!enSaldo) {
        PantallaLogin(
            cargando = cargando,
            error = error,
            onEntrar = { usuario, contrasena ->
                alcance.launch {
                    cargando = true; error = null
                    try {
                        val token = api.login(usuario, contrasena)
                        // VULNERABLE (a): se guardan usuario, contraseña y token en claro
                        almacen.guardar(usuario, contrasena, token)
                        enSaldo = true
                    } catch (e: Exception) {
                        error = describirError(e)
                    } finally {
                        cargando = false
                    }
                }
            }
        )
    } else {
        LaunchedEffect(intento) {
            cargando = true; error = null
            try {
                val token = almacen.token ?: throw IllegalStateException("Sin sesión")
                cuenta = try {
                    api.balance(token)
                } catch (e: ErrorApi) {
                    if (e.codigo != 401) throw e
                    // VULNERABLE: como la contraseña está guardada, la app vuelve a
                    // iniciar sesión sola cuando el token expira (OWASP M1/M9).
                    val nuevo = api.login(almacen.usuario.orEmpty(), almacen.contrasena.orEmpty())
                    almacen.actualizarToken(nuevo)
                    api.balance(nuevo)
                }
            } catch (e: Exception) {
                error = describirError(e)
            } finally {
                cargando = false
            }
        }
        PantallaSaldo(
            cuenta = cuenta,
            cargando = cargando,
            error = error,
            onReintentar = { intento++ },
            onCerrarSesion = {
                almacen.borrar()
                cuenta = null; error = null
                enSaldo = false
            }
        )
    }
}
