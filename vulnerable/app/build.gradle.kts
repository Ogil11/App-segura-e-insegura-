// =============================================================================
// MiBanco VULNERABLE - app deliberadamente insegura para la demo.
// AGP 9 trae Kotlin integrado: no hace falta el plugin org.jetbrains.kotlin.android.
// =============================================================================
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.demo.mibanco"
    compileSdk = 35
    // No se fija buildToolsVersion: AGP 9.5 exige build-tools >= 36.0.0 y usa su
    // versión por defecto (35.0.0 queda instalado pero AGP lo ignoraría).

    defaultConfig {
        applicationId = "com.demo.mibanco"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0-vulnerable"
    }

    buildTypes {
        release {
            // VULNERABLE: sin minify/R8 ni ofuscación; jadx muestra el código
            // (incluida la API key) tal cual se escribió (MASVS-RESILIENCE-3, OWASP M7/M8).
            isMinifyEnabled = false
            // Se firma con la llave de debug solo para poder instalarla en la demo.
            signingConfig = signingConfigs.getByName("debug")
        }
        // debug: android:debuggable="true" por defecto -> permite "adb shell run-as".
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.okhttp)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
