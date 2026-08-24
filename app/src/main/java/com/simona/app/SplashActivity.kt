package com.simona.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

/**
 * Splash inicial de SIMONA — Fase 6.1: migrado a la Splash Screen API
 * estándar de Android (androidx.core:core-splashscreen), reemplazando el
 * splash manual anterior (activity_splash.xml + Handler con delay fijo de
 * 900ms). Sigue siendo el launcher de la app: el sistema muestra el ícono
 * de marca (Theme.Simona.Splash en themes.xml) mientras el proceso
 * arranca, y deriva a MapaHuertasActivity apenas está lista.
 *
 * Decisión de diseño tomada con Ben: la API estándar no soporta texto
 * libre (el título "SIMONA" + subtítulo que tenía el splash viejo) — solo
 * ícono + color de fondo. Se optó por ir 100% nativo sin ese texto, en vez
 * de un híbrido que agregara una pantalla extra después. Por eso esta
 * Activity ya no infla ningún layout: installSplashScreen() se encarga de
 * todo el dibujo de la pantalla de marca antes de que onCreate() termine.
 *
 * activity_splash.xml y el string splash_subtitulo (strings.xml) quedan
 * sin uso — se pueden borrar del proyecto.
 *
 * Reemplaza a la vieja MainActivity (flujo de una sola huerta con
 * contraseña fija "simona123" — quedó obsoleta desde que existe la
 * arquitectura multi-huerta de la sección 14 del Plan de Desarrollo, pero
 * seguía siendo el launcher del manifest). Se puede borrar MainActivity.kt
 * y activity_main.xml del proyecto; ya no los referencia nada.
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Debe llamarse ANTES de super.onCreate(): es lo que le indica al
        // sistema que dibuje la splash nativa (definida en
        // Theme.Simona.Splash) mientras esta Activity termina de arrancar.
        installSplashScreen()

        super.onCreate(savedInstanceState)

        // SimonaApp.onCreate() ya inicializa el repo, pero lo tocamos acá
        // también para dejar explícito que la splash espera a que los
        // datos locales estén listos antes de navegar (hoy es instantáneo,
        // pero si HuertaRepository pasara a leer de un storage más lento,
        // acá es donde se engancharía un
        // splashScreen.setKeepOnScreenCondition { ... } para extender la
        // pantalla nativa hasta que los datos estén listos).
        (application as SimonaApp).huertaRepository

        irAHome()
    }

    private fun irAHome() {
        startActivity(Intent(this, MapaHuertasActivity::class.java))
        finish()
    }
}
