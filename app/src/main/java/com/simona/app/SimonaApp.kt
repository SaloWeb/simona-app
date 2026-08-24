
package com.simona.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Application de SIMONA. Mantiene instancias únicas de WifiConnectionManager
 * y HuertaRepository, compartidas entre todas las Activities:
 *
 * - WifiConnectionManager: porque bindProcess() ata la red a nivel de
 *   proceso, no de Activity individual (sección 5.2).
 * - HuertaRepository: para que cualquier pantalla lea/escriba siempre el
 *   mismo estado persistido de huertas (sección 14.4).
 *
 * Fase 6.5: acá también se crea el canal de notificaciones y se programa
 * el chequeo periódico de huertas con sed (AlertaHuertasWorker), porque es
 * trabajo de arranque que tiene que pasar una sola vez por proceso, igual
 * que las dos instancias de arriba.
 */
class SimonaApp : Application() {

    lateinit var wifiConnectionManager: WifiConnectionManager
        private set

    lateinit var huertaRepository: HuertaRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // Cambio (sección 14, múltiples huertas): ya no se fija una única
        // contraseña acá — cada huerta guarda la suya (ver Huerta.kt) y se
        // pasa a connect() en el momento de conectarse (ver 14.5).
        wifiConnectionManager = WifiConnectionManager(
            context = applicationContext,
            ssid = "SIMONA"
        )
        huertaRepository = HuertaRepository(applicationContext)

        crearCanalNotificaciones()
        programarChequeoPeriodico()
    }

    private fun crearCanalNotificaciones() {
        val canal = NotificationChannel(
            AlertaHuertasWorker.CANAL_ID,
            getString(R.string.notif_canal_nombre),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notif_canal_descripcion)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    /**
     * Cada 6 horas releé HuertaRepository y notifica si alguna huerta
     * sigue con sed (ver AlertaHuertasWorker). enqueueUniquePeriodicWork +
     * KEEP: si ya había un chequeo programado de una corrida anterior de
     * la app, no lo reinicia ni lo duplica en cada onCreate().
     */
    private fun programarChequeoPeriodico() {
        val restricciones = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val solicitud = PeriodicWorkRequestBuilder<AlertaHuertasWorker>(6, TimeUnit.HOURS)
            .setConstraints(restricciones)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            AlertaHuertasWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            solicitud
        )
    }
}

