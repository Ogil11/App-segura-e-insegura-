package com.demo.mibanco.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Etiqueta y color que distinguen esta versión durante la exposición. */
const val ETIQUETA_VERSION = "VERSIÓN VULNERABLE"
val COLOR_VERSION = Color(0xFFC62828)

private val Colores = lightColorScheme(
    primary = Color(0xFF0D47A1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFF00897B),
    background = Color(0xFFF4F6FA),
    surface = Color.White,
    error = Color(0xFFB00020),
)

@Composable
fun TemaMiBanco(contenido: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colores, content = contenido)
}
