
package com.simona.app

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.ImageView
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory

/**
 * Fase 6.4 — utilidades compartidas para la foto opcional de una huerta.
 * La usan tanto DatosHuertaActivity (D.3, al crear) como el diálogo de
 * editar de MapaHuertasActivity, y después el propio mapa compartido para
 * dibujarla en lugar del rectángulo de color del bancal.
 *
 * Sin Glide/Coil (sección 4.2, sin dependencias externas de más): decode
 * manual con BitmapFactory y submuestreo, para no cargar una foto de
 * cámara a resolución completa solo para mostrarla en un thumbnail chico.
 */
object FotoHuertaUtil {

    /**
     * Intenta dejar el permiso de lectura del URI elegido en el selector de
     * fotos vigente entre reinicios de la app. Si el proveedor no lo
     * soporta, no es grave: sigue funcionando mientras dure el proceso.
     */
    fun persistirPermisoLectura(resolver: ContentResolver, uri: Uri) {
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            // No crítico: la foto se sigue viendo en esta sesión igual.
        }
    }

    /**
     * Decodifica un Bitmap submuestreado a aproximadamente
     * [anchoObjetivoPx] x [altoObjetivoPx]. Devuelve null si el URI ya no
     * es accesible (permiso revocado, app reinstalada, etc.) en vez de
     * tirar la app abajo — quien llama debe tener un fallback visual.
     */
    fun decodificarSampleado(context: Context, uri: Uri, anchoObjetivoPx: Int, altoObjetivoPx: Int): Bitmap? {
        return try {
            val resolver = context.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: return null

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= anchoObjetivoPx &&
                bounds.outHeight / (sample * 2) >= altoObjetivoPx
            ) {
                sample *= 2
            }

            val opciones = BitmapFactory.Options().apply { inSampleSize = sample }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opciones) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Aplica [bitmap] a [imageView] con esquinas redondeadas (10dp, mismo
     * radio que bg_card_huerta/bg_bancal) para que se vea coherente con el
     * resto de la UI sin depender de clipToOutline.
     */
    fun aplicarConEsquinasRedondeadas(context: Context, imageView: ImageView, bitmap: Bitmap, radioDp: Float = 10f) {
        val drawable = RoundedBitmapDrawableFactory.create(context.resources, bitmap)
        drawable.cornerRadius = radioDp * context.resources.displayMetrics.density
        imageView.setImageDrawable(drawable)
    }
}
