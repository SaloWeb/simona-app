
package com.simona.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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
            (0 until arr.length()).map { huertaDesdeJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            // JSON corrupto o de un formato viejo: no tumbar la app, arrancar vacío.
            emptyList()
        }
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
        actuales[idx] = actuales[idx].copy(
            ultimaLectura = lectura,
            ultimaActualizacion = System.currentTimeMillis()
        )
        persistir(actuales)
    }

    private fun persistir(huertas: List<Huerta>) {
        val arr = JSONArray()
        huertas.forEach { arr.put(huertaAJson(it)) }
        prefs.edit().putString(KEY_HUERTAS, arr.toString()).apply()
    }

    private fun huertaAJson(h: Huerta): JSONObject = JSONObject().apply {
        put("id", h.id)
        put("nombre", h.nombre)
        put("passwordRed", h.passwordRed)
        put("categoria", h.categoria)
        put("humedadMin", h.humedadMin)
        put("humedadMax", h.humedadMax)
        put("phMin", h.phMin)
        put("phMax", h.phMax)
        put("luzMin", h.luzMin)
        put("luzMax", h.luzMax)
        put("tempMin", h.tempMin)
        put("tempMax", h.tempMax)
        put("ultimaActualizacion", h.ultimaActualizacion ?: JSONObject.NULL)
        put("posicionMapaX", h.posicionMapaX ?: JSONObject.NULL)
        put("posicionMapaY", h.posicionMapaY ?: JSONObject.NULL)
        put("fotoUri", h.fotoUri ?: JSONObject.NULL)
        h.ultimaLectura?.let { l ->
            put("ultimaLectura", JSONObject().apply {
                put("humedad", l.humedad)
                put("temperatura", l.temperatura)
                put("luz", l.luz)
                put("ph", l.ph)
                put("riegoActivo", l.riegoActivo)
                put("tanqueAgua", l.tanqueAgua)
            })
        }
    }

    private fun huertaDesdeJson(o: JSONObject): Huerta {
        val lectura = o.optJSONObject("ultimaLectura")?.let { l ->
            LecturaHuerta(
                humedad = l.getDouble("humedad").toFloat(),
                temperatura = l.getDouble("temperatura").toFloat(),
                luz = l.getInt("luz"),
                ph = l.getDouble("ph").toFloat(),
                riegoActivo = l.getBoolean("riegoActivo"),
                tanqueAgua = l.getDouble("tanqueAgua").toFloat()
            )
        }
        return Huerta(
            id = o.getString("id"),
            nombre = o.getString("nombre"),
            passwordRed = o.getString("passwordRed"),
            categoria = o.getString("categoria"),
            humedadMin = o.getDouble("humedadMin").toFloat(),
            humedadMax = o.getDouble("humedadMax").toFloat(),
            phMin = o.getDouble("phMin").toFloat(),
            phMax = o.getDouble("phMax").toFloat(),
            luzMin = o.getInt("luzMin"),
            luzMax = o.getInt("luzMax"),
            tempMin = o.getDouble("tempMin").toFloat(),
            tempMax = o.getDouble("tempMax").toFloat(),
            ultimaLectura = lectura,
            ultimaActualizacion = if (o.isNull("ultimaActualizacion")) null else o.getLong("ultimaActualizacion"),
            posicionMapaX = if (o.isNull("posicionMapaX")) null else o.getDouble("posicionMapaX").toFloat(),
            posicionMapaY = if (o.isNull("posicionMapaY")) null else o.getDouble("posicionMapaY").toFloat(),
            // optString con default: huertas guardadas antes de la 6.4 no
            // tienen esta clave y no deben romper el parseo (mismo criterio
            // que ya se usa para JSON corrupto/viejo más arriba).
            fotoUri = if (o.has("fotoUri") && !o.isNull("fotoUri")) o.getString("fotoUri") else null
        )
    }

    companion object {
        private const val PREFS_NAME = "simona_huertas"
        private const val KEY_HUERTAS = "huertas_json"
    }
}
