// Archivo de build raíz: solo declara los plugins (se aplican en :app).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
