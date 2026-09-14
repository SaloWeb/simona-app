
package com.simona.app

import java.util.UUID

/**
 * Modelo de una huerta (sección 14.4 del Plan de Desarrollo).
 * Cada huerta corresponde a un ESP32/servidor propio, identificado por su
 * contraseña de red (el SSID "SIMONA" es fijo y compartido entre todas).
 */
data class Huerta(
    val id: String = UUID.randomUUID().toString(),
    val nombre: String,
    val passwordRed: String,
    val categoria: String,
    val humedadMin: Float,
    val humedadMax: Float,
    val phMin: Float,
    val phMax: Float,
    val luzMin: Int,
    val luzMax: Int,
    val tempMin: Float,
    val tempMax: Float,
    val ultimaLectura: LecturaHuerta? = null,
    val ultimaActualizacion: Long? = null,
    // PLAN_MEJORAS_20.md, punto 8: serie temporal acotada (últimos
    // MAX_PUNTOS_HISTORIAL puntos, ver HuertaRepository) para alimentar
    // GraficoTendenciaView en DetalleHuertaActivity. Antes de esto la app
    // no tenía de dónde sacar una curva propia, solo ultimaLectura.
    val historial: List<PuntoHistorial> = emptyList(),
    // Paso 3 (opcional): posición del marcador en el croquis genérico
    val posicionMapaX: Float? = null,
    val posicionMapaY: Float? = null,
    // Fase 6.4 (opcional): foto propia de la huerta. Se guarda el URI como
    // String (content://...) porque HuertaRepository serializa a JSON; se
    // usa en el mapa compartido (MapaHuertasActivity) para reemplazar el
    // rectángulo de color del bancal por esta foto. Se puede elegir/cambiar
    // tanto al crear la huerta (DatosHuertaActivity) como al editarla.
    val fotoUri: String? = null
)

/**
 * Última lectura conocida de una huerta (sección 14.4). El mapa (14.6) y la
 * lista de huertas leen siempre de acá, no de una conexión en vivo.
 */
data class LecturaHuerta(
    val humedad: Float,
    val temperatura: Float,
    val luz: Int,
    val ph: Float,
    val riegoActivo: Boolean,
    val tanqueAgua: Float
)

/**
 * Un punto de la serie histórica de una huerta, usado por GraficoTendenciaView
 * para trazar la curva de humedad/temperatura a lo largo del tiempo.
 */
data class PuntoHistorial(
    val timestamp: Long,
    val humedad: Float,
    val temperatura: Float
)
