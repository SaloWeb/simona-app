package com.simona.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Serialización/deserialización de [Huerta] a/desde JSON, extraída de
 * [HuertaRepository] (PLAN_MEJORAS_20.md, punto 16) para poder testearla
 * como lógica pura de JVM, sin necesitar un Context de Android ni
 * SharedPreferences reales (Robolectric). [HuertaRepository] sigue siendo
 * el único responsable de leer/escribir esos JSON en disco.
 */
object HuertaJson {

    fun aJson(h: Huerta): JSONObject = JSONObject().apply {
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
        // PLAN_MEJORAS_20.md, punto 8: serie temporal acotada para
        // GraficoTendenciaView. Array de objetos (no 3 arrays paralelos)
        // para que un punto nunca pueda desalinearse de su timestamp.
        put("historial", JSONArray().apply {
            h.historial.forEach { p ->
                put(JSONObject().apply {
                    put("timestamp", p.timestamp)
                    put("humedad", p.humedad)
                    put("temperatura", p.temperatura)
                })
            }
        })
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

    fun desdeJson(o: JSONObject): Huerta {
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
            // Huertas guardadas antes del punto 8 no tienen esta clave:
            // mismo criterio de tolerancia que fotoUri, arranca vacío.
            historial = if (o.has("historial") && !o.isNull("historial")) {
                val arr = o.getJSONArray("historial")
                (0 until arr.length()).map { i ->
                    val p = arr.getJSONObject(i)
                    PuntoHistorial(
                        timestamp = p.getLong("timestamp"),
                        humedad = p.getDouble("humedad").toFloat(),
                        temperatura = p.getDouble("temperatura").toFloat()
                    )
                }
            } else emptyList(),
            posicionMapaX = if (o.isNull("posicionMapaX")) null else o.getDouble("posicionMapaX").toFloat(),
            posicionMapaY = if (o.isNull("posicionMapaY")) null else o.getDouble("posicionMapaY").toFloat(),
            // optString con default: huertas guardadas antes de la 6.4 no
            // tienen esta clave y no deben romper el parseo (mismo criterio
            // que ya se usa para JSON corrupto/viejo en HuertaRepository).
            fotoUri = if (o.has("fotoUri") && !o.isNull("fotoUri")) o.getString("fotoUri") else null
        )
    }
}
