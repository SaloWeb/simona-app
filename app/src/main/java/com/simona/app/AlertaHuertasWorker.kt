
package com.simona.app

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Fase 6.5 — chequeo periódico en background de huertas con sed.
 *
 * Importante: este worker NO se reconecta al WiFi del ESP32 para pedir una
 * lectura fresca. Esa conexión (WifiConnectionManager, sección 5.2) solo
 * existe mientras el usuario tiene abierto TutorialConexionActivity o
 * DashboardActivity — no tiene sentido mantenerla ni recrearla en
 * background para un chequeo cada varias horas. En cambio, relee la
 * ÚLTIMA lectura que ya quedó persistida en HuertaRepository (14.4) —la
 * misma fuente que usa MapaHuertasActivity para pintar "con sed" en la
 * lista y el mapa— y notifica si sigue por debajo de humedadMin.
 *
 * El canal de notificaciones se crea una sola vez en SimonaApp.onCreate();
 * acá se asume que ya existe.
 */
class AlertaHuertasWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val huertasConSed = HuertaRepository(applicationContext).listar().filter { huerta ->
            val lectura = huerta.ultimaLectura
            lectura != null && lectura.humedad <= huerta.humedadMin
        }

        if (huertasConSed.isNotEmpty()) {
            notificar(huertasConSed)
        }

        // Result.success() aunque no haya nada que avisar: no es un error,
        // simplemente no había ninguna huerta con sed en este chequeo.
        return Result.success()
    }

    private fun notificar(huertasConSed: List<Huerta>) {
        val context = applicationContext

        // En Android 13+ postear una notificación exige este permiso en
        // tiempo de ejecución (se pide en MapaHuertasActivity). Si el
        // usuario lo negó, no reintentamos acá — solo evitamos la
        // SecurityException y salimos en silencio.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val titulo = if (huertasConSed.size == 1) {
            context.getString(R.string.notif_titulo_una_huerta, huertasConSed[0].nombre)
        } else {
            context.getString(R.string.notif_titulo_varias_huertas, huertasConSed.size)
        }
        val texto = huertasConSed.joinToString(", ") { it.nombre }

        val intent = Intent(context, MapaHuertasActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificacion = NotificationCompat.Builder(context, CANAL_ID)
            .setSmallIcon(R.drawable.ic_alerta)
            .setColor(ContextCompat.getColor(context, R.color.simona_marron))
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIF_ID, notificacion)
    }

    companion object {
        const val CANAL_ID = "alertas_huertas"
        const val WORK_NAME = "chequeo_periodico_huertas"
        private const val NOTIF_ID = 1001
    }
}
