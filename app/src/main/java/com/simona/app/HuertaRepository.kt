
package com.simona.app

import android.content.Context
import android.util.Log
import org.json.JSONArray

/**
 * Persistencia local de huertas (sección 14.4). Se eligió SharedPreferences
 * con serialización manual en JSON (org.json, incluido en el SDK de
 * Android) en vez de Room, coherente con el criterio de simplicidad ya
 * usado en el resto del proyecto (sin dependencias externas — sección 4.2).
 *
 * Vive como instancia única dentro de SimonaApp, igual que
 * WifiConnectionManager (sección 5.2), para que cualquier Activity lea/
 * escriba siempre el mismo estado.
 */
class HuertaRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun listar(): List<Huerta> {
        val json = prefs.getString(KEY_HUERTAS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val huerta = HuertaJson.desdeJson(arr.getJSONObject(it))
                // PLAN_MEJORAS_20.md, punto 4: descifrar acá, fuera de
                // HuertaJson (que se mantiene puro/testeable sin Keystore).
                huerta.copy(passwordRed = CryptoUtil.decryptIfNeeded(huerta.passwordRed))
            }
        } catch (e: Exception) {
            // JSON corrupto o de un formato viejo: no tumbar la app, arrancar vacío.
            // PLAN_MEJORAS_20.md, punto 12: en vez de perder el dato sin que
            // nadie se entere, se loguea, se guarda un backup crudo del JSON
            // que falló (por si hay que rescatarlo a mano después) y se deja
            // una bandera para que la UI avise una única vez.
            Log.e(TAG, "JSON corrupto en '$KEY_HUERTAS', se arranca con lista vacía. Backup guardado en '$KEY_BACKUP_CORRUPTO'.", e)
            prefs.edit()
                .putString(KEY_BACKUP_CORRUPTO, json)
                .putBoolean(KEY_HUBO_CORRUPCION, true)
                .apply()
            emptyList()
        }
    }

    /**
     * True una única vez si la última vez que se leyó el JSON estaba
     * corrupto (ver [listar]). Pensándolo para que la UI (ej.
     * MapaHuertasActivity) muestre un aviso no bloqueante una sola vez y
     * no se repita en cada `onResume`. El JSON crudo que falló queda en
     * SharedPreferences bajo [KEY_BACKUP_CORRUPTO] por si hace falta
     * rescatarlo manualmente (no se expone por API porque es un caso raro
     * de soporte, no un flujo normal de la app).
     */
    @Synchronized
    fun consumirAvisoDeCorrupcion(): Boolean {
        val hubo = prefs.getBoolean(KEY_HUBO_CORRUPCION, false)
        if (hubo) prefs.edit().putBoolean(KEY_HUBO_CORRUPCION, false).apply()
        return hubo
    }

    @Synchronized
    fun obtener(id: String): Huerta? = listar().find { it.id == id }

    @Synchronized
    fun guardar(huerta: Huerta) {
        val actuales = listar().toMutableList()
        val idx = actuales.indexOfFirst { it.id == huerta.id }
        if (idx >= 0) actuales[idx] = huerta else actuales.add(huerta)
        persistir(actuales)
    }

    @Synchronized
    fun eliminar(id: String) {
        persistir(listar().filterNot { it.id == id })
    }

    @Synchronized
    fun actualizarLectura(id: String, lectura: LecturaHuerta) {
        val actuales = listar().toMutableList()
        val idx = actuales.indexOfFirst { it.id == id }
        if (idx < 0) return // huerta borrada mientras seguía conectada: ignorar
        val ahora = System.currentTimeMillis()
        // PLAN_MEJORAS_20.md, punto 8: acumula la serie temporal acotada a
        // MAX_PUNTOS_HISTORIAL, descartando los puntos más viejos primero
        // (misma idea que el historial en memoria del simulador, pero acá
        // vive persistido por huerta en vez de compartido por proceso).
        val nuevoHistorial = (actuales[idx].historial + PuntoHistorial(
            timestamp = ahora,
            humedad = lectura.humedad,
            temperatura = lectura.temperatura
        )).takeLast(MAX_PUNTOS_HISTORIAL)
        actuales[idx] = actuales[idx].copy(
            ultimaLectura = lectura,
            ultimaActualizacion = ahora,
            historial = nuevoHistorial
        )
        persistir(actuales)
    }

    private fun persistir(huertas: List<Huerta>) {
        val arr = JSONArray()
        huertas.forEach { huerta ->
            // PLAN_MEJORAS_20.md, punto 4: cifrar la contraseña de red antes
            // de que HuertaJson la serialice. Las huertas en memoria (las
            // que ve el resto de la app: DetalleHuertaActivity, diálogo de
            // editar, etc.) siguen viajando con la contraseña en texto
            // plano — el cifrado es puramente un detalle de almacenamiento.
            val huertaParaGuardar = huerta.copy(passwordRed = CryptoUtil.encrypt(huerta.passwordRed))
            arr.put(HuertaJson.aJson(huertaParaGuardar))
        }
        prefs.edit().putString(KEY_HUERTAS, arr.toString()).apply()
    }

    companion object {
        private const val TAG = "HuertaRepository"
        private const val PREFS_NAME = "simona_huertas"
        private const val KEY_HUERTAS = "huertas_json"
        private const val KEY_BACKUP_CORRUPTO = "huertas_json_backup_corrupto"
        private const val KEY_HUBO_CORRUPCION = "hubo_corrupcion_pendiente_aviso"
        // PLAN_MEJORAS_20.md, punto 8: entre 50 y 100 puntos sugeridos por
        // el plan; con lecturas cada ~3s (polling de DashboardActivity) 80
        // puntos son ~4 minutos de curva reciente, suficiente para que
        // GraficoTendenciaView muestre una tendencia útil sin que el JSON
        // de SharedPreferences crezca sin límite con el uso continuo.
        const val MAX_PUNTOS_HISTORIAL = 80
    }
}
