package com.simona.app

/**
 * Validaciones de los pasos de alta de huerta (PLAN_MEJORAS_20.md, punto 16),
 * extraídas de [AjustarRangosActivity] y [DatosHuertaActivity] a funciones
 * puras de JVM (sin Context ni vistas de Android) para poder testearlas sin
 * Robolectric. La lógica en sí no cambió, solo de dónde vive.
 */
object ValidacionesHuerta {

    /** Ancho mínimo de 10 puntos entre umbral bajo y alto de humedad — es la
     * única variable con actuador físico real (el riego), ver
     * AjustarRangosActivity.aplicarAnchoMinimoHumedad(). */
    const val ANCHO_MINIMO_HUMEDAD = 10f

    /** WPA2 exige mínimo 8 caracteres: WifiNetworkSpecifier.setWpa2Passphrase()
     * lanza IllegalArgumentException con contraseñas más cortas. */
    const val LARGO_MINIMO_PASSWORD = 8

    fun nombreValido(nombre: String): Boolean = nombre.trim().isNotEmpty()

    fun passwordValida(password: String): Boolean = password.length >= LARGO_MINIMO_PASSWORD

    /** min < max, sin restricción de ancho — aplica a pH, luz y temperatura. */
    fun rangoValido(min: Float, max: Float): Boolean = min < max

    /** Igual que [rangoValido] pero exigiendo además el ancho mínimo — hoy
     * solo se usa para humedad, pero queda general por si se necesita para
     * otra variable con actuador en el futuro. */
    fun rangoConAnchoMinimoValido(min: Float, max: Float, anchoMinimo: Float): Boolean =
        min < max && (max - min) >= anchoMinimo

    fun anchoHumedadValido(min: Float, max: Float): Boolean =
        rangoConAnchoMinimoValido(min, max, ANCHO_MINIMO_HUMEDAD)

    /** Valida los 4 rangos de una huerta de una sola vez (usado al confirmar
     * en AjustarRangosActivity): humedad con ancho mínimo, el resto solo
     * min < max. */
    fun rangosDeHuertaValidos(
        humedadMin: Float, humedadMax: Float,
        phMin: Float, phMax: Float,
        luzMin: Float, luzMax: Float,
        tempMin: Float, tempMax: Float
    ): Boolean =
        anchoHumedadValido(humedadMin, humedadMax) &&
            rangoValido(phMin, phMax) &&
            rangoValido(luzMin, luzMax) &&
            rangoValido(tempMin, tempMax)
}
