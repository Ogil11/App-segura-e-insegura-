// =============================================================================
// MiBanco SEGURA - versión blindada de la misma app.
// AGP 9 trae Kotlin integrado: no hace falta el plugin org.jetbrains.kotlin.android.
// =============================================================================
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.demo.mibanco.segura"
    compileSdk = 35
    // No se fija buildToolsVersion: AGP 9.5 exige build-tools >= 36.0.0.

    defaultConfig {
        // applicationId distinto -> conviven instaladas la vulnerable y la segura.
        applicationId = "com.demo.mibanco.segura"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0-segura"
    }

    buildTypes {
        release {
            // SEGURO: R8 activo (minify + ofuscación + eliminación de código muerto)
            // y recursos sin usar eliminados (MASVS-RESILIENCE-3).
            // OJO: la ofuscación solo DIFICULTA la ingeniería inversa, no la impide.
            // Por eso la defensa real es no tener secretos en el APK: la sesión la
            // emite el backend en /login (MASVS-STORAGE-1, OWASP M1).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // SEGURO: release NO es debuggable (valor por defecto; se deja explícito).
            isDebuggable = false
            // Solo para poder instalarla en la demo se firma con la llave de debug.
            // En producción se usa una llave de subida propia + Play App Signing.
            signingConfig = signingConfigs.getByName("debug")
        }
        // Nota: el build debug SÍ es debuggable (para poder ejecutar "run-as" en la
        // demo y mostrar que el token está cifrado). Nunca se publica un debug.
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
    // BiometricPrompt necesita una FragmentActivity; se fija una versión reciente de fragment.
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.okhttp)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
