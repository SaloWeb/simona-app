
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.simona.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.simona.app"
        // minSdk 29 (Android 10): WifiNetworkSpecifier no requiere permiso de ubicación
        // a partir de esta versión (ver Plan de Desarrollo, sección 5.3).
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // Sin dependencias externas para WebView ni conexión WiFi: ambas son APIs
    // que ya trae el SDK de Android (android.webkit.WebView y
    // android.net.ConnectivityManager). Ver Plan de Desarrollo, sección 4.2.
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    // Fase 6.1: Splash Screen API estándar (Android 12+, compatible hacia
    // atrás hasta minSdk 29 vía la librería de compat). Reemplaza el splash
    // manual con Handler+delay de SplashActivity.kt.
    implementation("androidx.core:core-splashscreen:1.0.1")
    // Fase 6.5: worker periódico que relee HuertaRepository y notifica si
    // alguna huerta sigue con sed — no agrega Firebase ni servicios en la
    // nube, solo el scheduler local ya recomendado por Android para tareas
    // en background garantizadas (sección 4.2, sin dependencias externas
    // de más allá de lo estrictamente necesario).
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
