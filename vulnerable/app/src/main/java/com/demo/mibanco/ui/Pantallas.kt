package com.demo.mibanco.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.demo.mibanco.datos.Cuenta
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

// =============================================================================
// Pantallas compartidas: el MISMO diseño en la app vulnerable y en la segura,
// para que la comparación sea justa (solo cambia lo que pasa por debajo).
// =============================================================================

@Composable
private fun Encabezado(subtitulo: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.primary, Color(0xFF1565C0))
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Text("MiBanco", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(subtitulo, color = Color.White.copy(alpha = 0.85f))
        Spacer(Modifier.height(10.dp))
        Text(
            ETIQUETA_VERSION,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(COLOR_VERSION, RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun MensajeError(texto: String?) {
    if (texto == null) return
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFDECEA)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(texto, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(14.dp))
    }
}

/**
 * Pantalla de inicio de sesión.
 * @param aviso contenido opcional (p. ej. aviso de biometría no registrada en la app segura).
 * @param habilitado false para bloquear el botón (p. ej. sin biometría).
 */
@Composable
fun PantallaLogin(
    cargando: Boolean,
    error: String?,
    onEntrar: (usuario: String, contrasena: String) -> Unit,
    habilitado: Boolean = true,
    aviso: (@Composable () -> Unit)? = null,
) {
    var usuario by rememberSaveable { mutableStateOf("") }
    var contrasena by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        Encabezado("Tu banca, siempre contigo")
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Iniciar sesión", style = MaterialTheme.typography.headlineSmall)
            aviso?.invoke()
            OutlinedTextField(
                value = usuario,
                onValueChange = { usuario = it },
                label = { Text("Usuario") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = contrasena,
                onValueChange = { contrasena = it },
                label = { Text("Contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password, imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )
            MensajeError(error)
            Button(
                onClick = { onEntrar(usuario.trim(), contrasena) },
                enabled = habilitado && !cargando && usuario.isNotBlank() && contrasena.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (cargando) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Ingresar")
            }
            Text(
                "Credenciales de demostración: demo / demo1234",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

private fun formatearSaldo(valor: Double, moneda: String): String {
    val f = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO"))
    runCatching { f.currency = Currency.getInstance(moneda) }
    return f.format(valor)
}

/**
 * Pantalla de saldo.
 * @param bloqueado true si hay que autenticarse antes de mostrar datos (app segura).
 */
@Composable
fun PantallaSaldo(
    cuenta: Cuenta?,
    cargando: Boolean,
    error: String?,
    onCerrarSesion: () -> Unit,
    onReintentar: () -> Unit,
    bloqueado: Boolean = false,
    onDesbloquear: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        Encabezado(if (cuenta != null) "Hola, ${cuenta.titular}" else "Mi cuenta")
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(22.dp)) {
                    Text("Cuenta de ahorros", color = Color.Gray)
                    Text(
                        cuenta?.cuenta ?: "•••• •••• •••• ••••",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(18.dp))
                    Text("Saldo disponible", color = Color.Gray)
                    Box(Modifier.height(48.dp), contentAlignment = Alignment.CenterStart) {
                        when {
                            cargando -> CircularProgressIndicator(Modifier.size(28.dp))
                            cuenta != null -> Text(
                                formatearSaldo(cuenta.saldo, cuenta.moneda),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            else -> Text("$ ••••••", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
                        }
                    }
                    if (cuenta != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("Titular: ${cuenta.titular}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            MensajeError(error)
            if (bloqueado && !cargando) {
                Button(onClick = onDesbloquear, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Ver saldo con huella")
                }
            } else if (error != null && !cargando) {
                Button(onClick = onReintentar, modifier = Modifier.fillMaxWidth()) { Text("Reintentar") }
            }
            OutlinedButton(onClick = onCerrarSesion, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Cerrar sesión")
            }
        }
    }
}
