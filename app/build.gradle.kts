
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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // PLAN_MEJORAS_20.md, punto 5: sin shrink/ofuscación el .apk queda
            // más pesado de lo necesario y cualquiera puede decompilarlo sin
            // esfuerzo. Ver proguard-rules.pro por la regla de WorkManager
            // necesaria para que AlertaHuertasWorker no se rompa con esto.
            isMinifyEnabled = true
            isShrinkResources = true
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
    // PLAN_MEJORAS_20.md, punto 7: lista de huertas del Home migrada de
    // LinearLayout+Views a mano a RecyclerView+ListAdapter/DiffUtil.
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // PLAN_MEJORAS_UX_20.md, punto 15: pull-to-refresh en la lista de
    // huertas del Home (antes la única forma de refrescar era volver a
    // entrar a la pantalla, onResume). Librería oficial de AndroidX,
    // liviana (un solo widget), consistente con el resto de dependencias
    // del proyecto (todas androidx/Material de primera parte).
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
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

    // Tests unitarios de JVM (PLAN_MEJORAS_20.md, punto 16). org.json:json
    // es necesario porque el android.jar de test usa un stub vacío de
    // org.json que tira excepción al usarse — esta dependencia pone la
    // implementación real primero en el classpath de test.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    // Instrumentation tests (Espresso / AndroidX Test)
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-idling-resource:3.5.1")
}
