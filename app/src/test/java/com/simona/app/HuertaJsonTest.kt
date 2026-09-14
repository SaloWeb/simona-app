package com.simona.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la serialización JSON de [Huerta] (PLAN_MEJORAS_20.md, punto 16).
 * Corren como JVM puro (sin Robolectric): [HuertaJson] no depende de
 * Context ni de SharedPreferences, solo de org.json.
 */
class HuertaJsonTest {

    private fun huertaDeEjemplo(
        ultimaLectura: LecturaHuerta? = null,
        fotoUri: String? = null,
        posicionMapaX: Float? = null,
        posicionMapaY: Float? = null,
        ultimaActualizacion: Long? = null,
        historial: List<PuntoHistorial> = emptyList()
    ) = Huerta(
        id = "huerta-1",
        nombre = "Tomates del fondo",
        passwordRed = "clave1234",
        categoria = "Fruto",
        humedadMin = 40f,
        humedadMax = 70f,
        phMin = 5.5f,
        phMax = 7.0f,
        luzMin = 200,
        luzMax = 800,
        tempMin = 15f,
        tempMax = 28f,
        ultimaLectura = ultimaLectura,
        ultimaActualizacion = ultimaActualizacion,
        posicionMapaX = posicionMapaX,
        posicionMapaY = posicionMapaY,
        fotoUri = fotoUri,
        historial = historial
    )

    @Test
    fun `round trip sin lectura ni campos opcionales preserva todos los valores`() {
        val original = huertaDeEjemplo()

        val recuperada = HuertaJson.desdeJson(HuertaJson.aJson(original))

        assertEquals(original, recuperada)
    }

    @Test
    fun `round trip con ultima lectura y campos opcionales preserva todos los valores`() {
        val original = huertaDeEjemplo(
            ultimaLectura = LecturaHuerta(
                humedad = 55.5f,
                temperatura = 21.3f,
                luz = 450,
                ph = 6.2f,
                riegoActivo = true,
                tanqueAgua = 80f
            ),
            fotoUri = "content://media/external/images/42",
            posicionMapaX = 0.35f,
            posicionMapaY = 0.72f,
            ultimaActualizacion = 1_757_000_000_000L
        )

        val recuperada = HuertaJson.desdeJson(HuertaJson.aJson(original))

        assertEquals(original, recuperada)
    }

    @Test
    fun `huerta sin fotoUri en el JSON (formato viejo, previo a 6_4) no rompe el parseo`() {
        // Simula una huerta guardada ANTES de que existiera el campo fotoUri,
        // sacando esa clave a mano del JSON (no pasa por aJson()).
        val json = HuertaJson.aJson(huertaDeEjemplo())
        json.remove("fotoUri")

        val recuperada = HuertaJson.desdeJson(json)

        assertNull(recuperada.fotoUri)
    }

    @Test
    fun `huerta sin posicion en el mapa (nunca la ubico) queda con posicion null`() {
        val json = HuertaJson.aJson(huertaDeEjemplo())

        val recuperada = HuertaJson.desdeJson(json)

        assertNull(recuperada.posicionMapaX)
        assertNull(recuperada.posicionMapaY)
    }

    @Test(expected = org.json.JSONException::class)
    fun `JSON sin campos obligatorios (ej nombre) tira excepcion en vez de devolver datos truchos`() {
        // Esto es lo que hace que HuertaRepository.listar() caiga a lista
        // vacía: si un campo obligatorio falta, tiene que explotar acá.
        val jsonIncompleto = JSONObject().apply {
            put("id", "huerta-1")
            // falta "nombre" y el resto de los campos obligatorios a propósito
        }

        HuertaJson.desdeJson(jsonIncompleto)
    }

    @Test
    fun `round trip con historial preserva orden y valores de cada punto`() {
        val original = huertaDeEjemplo(
            historial = listOf(
                PuntoHistorial(timestamp = 1_000L, humedad = 55f, temperatura = 20f),
                PuntoHistorial(timestamp = 2_000L, humedad = 57f, temperatura = 20.5f),
                PuntoHistorial(timestamp = 3_000L, humedad = 56f, temperatura = 21f)
            )
        )

        val recuperada = HuertaJson.desdeJson(HuertaJson.aJson(original))

        assertEquals(original.historial, recuperada.historial)
    }

    @Test
    fun `huerta sin historial en el JSON (formato previo al punto 8) queda con lista vacia`() {
        // Simula una huerta guardada antes de que existiera "historial",
        // sacando esa clave a mano del JSON (no pasa por aJson()).
        val json = HuertaJson.aJson(huertaDeEjemplo())
        json.remove("historial")

        val recuperada = HuertaJson.desdeJson(json)

        assertEquals(emptyList<PuntoHistorial>(), recuperada.historial)
    }

    @Test
    fun `serializa riegoActivo como boolean real, no como string`() {
        val original = huertaDeEjemplo(
            ultimaLectura = LecturaHuerta(
                humedad = 10f, temperatura = 10f, luz = 10, ph = 6f,
                riegoActivo = true, tanqueAgua = 10f
            )
        )

        val json = HuertaJson.aJson(original)
        val lectura = json.getJSONObject("ultimaLectura")

        assertTrue(lectura.get("riegoActivo") is Boolean)
        assertEquals(true, lectura.getBoolean("riegoActivo"))
    }
}
