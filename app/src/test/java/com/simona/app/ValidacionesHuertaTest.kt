package com.simona.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [ValidacionesHuerta] (PLAN_MEJORAS_20.md, punto 16), la lógica
 * extraída de DatosHuertaActivity y AjustarRangosActivity. JVM puro, sin
 * Robolectric ni dependencias de Android.
 */
class ValidacionesHuertaTest {

    // --- nombreValido (DatosHuertaActivity) ---

    @Test
    fun `nombre vacio es invalido`() {
        assertFalse(ValidacionesHuerta.nombreValido(""))
    }

    @Test
    fun `nombre solo con espacios es invalido`() {
        assertFalse(ValidacionesHuerta.nombreValido("   "))
    }

    @Test
    fun `nombre con contenido es valido`() {
        assertTrue(ValidacionesHuerta.nombreValido("Tomates del fondo"))
    }

    // --- passwordValida (DatosHuertaActivity, WPA2 minimo 8 caracteres) ---

    @Test
    fun `password de menos de 8 caracteres es invalida`() {
        assertFalse(ValidacionesHuerta.passwordValida("abc123"))
    }

    @Test
    fun `password de exactamente 8 caracteres es valida`() {
        assertTrue(ValidacionesHuerta.passwordValida("abcd1234"))
    }

    @Test
    fun `password vacia es invalida`() {
        assertFalse(ValidacionesHuerta.passwordValida(""))
    }

    // --- anchoHumedadValido / rangoConAnchoMinimoValido (AjustarRangosActivity) ---

    @Test
    fun `ancho de humedad menor a 10 puntos es invalido`() {
        assertFalse(ValidacionesHuerta.anchoHumedadValido(40f, 45f))
    }

    @Test
    fun `ancho de humedad de exactamente 10 puntos es valido`() {
        assertTrue(ValidacionesHuerta.anchoHumedadValido(40f, 50f))
    }

    @Test
    fun `ancho de humedad mayor a 10 puntos es valido`() {
        assertTrue(ValidacionesHuerta.anchoHumedadValido(20f, 80f))
    }

    @Test
    fun `humedad con min mayor o igual a max es invalida aunque el ancho de sobre`() {
        // Caso borde defensivo: min >= max no debería pasarle al slider en la
        // UI real, pero la función no debe dar un falso positivo si alguna
        // vez se llama con valores invertidos.
        assertFalse(ValidacionesHuerta.anchoHumedadValido(50f, 40f))
    }

    // --- rangoValido (pH, luz, temperatura — sin ancho mínimo) ---

    @Test
    fun `rango con min menor a max es valido`() {
        assertTrue(ValidacionesHuerta.rangoValido(5.5f, 7.0f))
    }

    @Test
    fun `rango con min igual a max es invalido`() {
        assertFalse(ValidacionesHuerta.rangoValido(6.0f, 6.0f))
    }

    @Test
    fun `rango con min mayor a max es invalido`() {
        assertFalse(ValidacionesHuerta.rangoValido(7.0f, 5.5f))
    }

    // --- rangosDeHuertaValidos (confirmarRangos() en AjustarRangosActivity) ---

    @Test
    fun `los 4 rangos de un perfil tipico son validos en conjunto`() {
        assertTrue(
            ValidacionesHuerta.rangosDeHuertaValidos(
                humedadMin = 40f, humedadMax = 70f,
                phMin = 5.5f, phMax = 7.0f,
                luzMin = 200f, luzMax = 800f,
                tempMin = 15f, tempMax = 28f
            )
        )
    }

    @Test
    fun `rangos invalidos si la humedad no cumple el ancho minimo aunque el resto este bien`() {
        assertFalse(
            ValidacionesHuerta.rangosDeHuertaValidos(
                humedadMin = 40f, humedadMax = 45f,
                phMin = 5.5f, phMax = 7.0f,
                luzMin = 200f, luzMax = 800f,
                tempMin = 15f, tempMax = 28f
            )
        )
    }

    @Test
    fun `rangos invalidos si temperatura tiene min igual a max aunque humedad este bien`() {
        assertFalse(
            ValidacionesHuerta.rangosDeHuertaValidos(
                humedadMin = 40f, humedadMax = 70f,
                phMin = 5.5f, phMax = 7.0f,
                luzMin = 200f, luzMax = 800f,
                tempMin = 20f, tempMax = 20f
            )
        )
    }
}
