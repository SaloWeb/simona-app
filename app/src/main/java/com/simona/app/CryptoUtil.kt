package com.simona.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * PLAN_MEJORAS_20.md, punto 4: cifra `passwordRed` (contraseñas de red
 * WiFi de cada huerta) usando una clave AES-256 guardada en el Android
 * Keystore del dispositivo (nunca sale del hardware de seguridad, ni
 * siquiera esta clase la puede leer — solo puede pedirle al Keystore que
 * cifre/descifre con ella).
 *
 * Se implementa directo contra Android Keystore en vez de agregar
 * `androidx.security:security-crypto`: esa librería está en proceso de
 * deprecación por parte de Google en favor de esto mismo (uso directo del
 * Keystore), así que sumarla ahora sería agregar una dependencia externa
 * que además ya está de salida — no tiene sentido para un proyecto que
 * evita dependencias externas de entrada (ver criterio en
 * CONTEXTO_PROYECTO.md, sección 11).
 *
 * Formato de un valor cifrado: "enc:v1:<iv en base64>:<ciphertext en
 * base64>". El prefijo "enc:v1:" es lo que permite [decryptIfNeeded]
 * distinguir un valor ya cifrado de una contraseña vieja guardada en
 * texto plano ANTES de este cambio — así las huertas ya existentes no se
 * rompen: se leen tal cual (texto plano) y se re-guardan cifradas la
 * próxima vez que se llame a [HuertaRepository.guardar] (migración
 * perezosa, sin necesitar un paso de migración explícito al abrir la app).
 */
object CryptoUtil {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "simona_password_key"
    private const val TRANSFORMACION = "AES/GCM/NoPadding"
    private const val TAMANO_TAG_GCM_BITS = 128
    private const val PREFIJO = "enc:v1:"

    private fun obtenerOCrearClave(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generador.init(spec)
        return generador.generateKey()
    }

    /** Cifra [texto] y devuelve el valor con el prefijo [PREFIJO]. */
    fun encrypt(texto: String): String {
        val cipher = Cipher.getInstance(TRANSFORMACION).apply {
            init(Cipher.ENCRYPT_MODE, obtenerOCrearClave())
        }
        val cifrado = cipher.doFinal(texto.toByteArray(Charsets.UTF_8))
        val ivB64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val cifradoB64 = Base64.encodeToString(cifrado, Base64.NO_WRAP)
        return "$PREFIJO$ivB64:$cifradoB64"
    }

    /**
     * Descifra [valor] si tiene el prefijo [PREFIJO]; si no (contraseña
     * vieja en texto plano, guardada antes de este cambio), lo devuelve
     * tal cual. Si el descifrado falla por cualquier motivo (clave
     * borrada del Keystore, dato corrupto), devuelve el valor tal cual en
     * vez de tirar una excepción — se prefiere mostrar una contraseña
     * potencialmente incorrecta a romper la carga de huertas por esto.
     */
    fun decryptIfNeeded(valor: String): String {
        if (!valor.startsWith(PREFIJO)) return valor
        return try {
            val partes = valor.removePrefix(PREFIJO).split(":", limit = 2)
            val iv = Base64.decode(partes[0], Base64.NO_WRAP)
            val cifrado = Base64.decode(partes[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMACION).apply {
                init(Cipher.DECRYPT_MODE, obtenerOCrearClave(), GCMParameterSpec(TAMANO_TAG_GCM_BITS, iv))
            }
            String(cipher.doFinal(cifrado), Charsets.UTF_8)
        } catch (e: Exception) {
            valor
        }
    }
}
