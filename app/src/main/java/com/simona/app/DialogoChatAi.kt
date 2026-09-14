package com.simona.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simona.app.databinding.DialogChatAiBinding

/**
 * Diálogo del asistente de IA "Simona", accesible desde el FAB del Home
 * (contexto de TODAS las huertas) y desde DetalleHuertaActivity en modo
 * conectado (contexto de UNA sola huerta, la que está abierta) — extraído
 * de MapaHuertasActivity.abrirChatAi() a su propia clase (PLAN_MEJORAS_20.md,
 * punto 7). Autocontenido: no depende del picker de fotos ni de ningún
 * otro estado que viva en la Activity que lo invoca.
 */
object DialogoChatAi {

    /**
     * huertaIdEnfoque: si se pasa (DetalleHuertaActivity en modo conectado),
     * el contexto que recibe Gemini se arma alrededor de ESA huerta puntual
     * en vez de un resumen de la flota completa — mismo criterio que tenía
     * la ya eliminada DashboardActivity.armarContextoHuerta(), pero
     * reutilizando este mismo diálogo en vez de duplicar la UI de chat.
     */
    fun mostrar(activity: AppCompatActivity, repository: HuertaRepository, huertaIdEnfoque: String? = null) {
        val binding = DialogChatAiBinding.inflate(LayoutInflater.from(activity))

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(binding.root)
            .setCancelable(true)
            .create()

        // PLAN_MEJORAS_VISUAL_2.md, punto 4: scrollChat pasó de 200dp fijo
        // a wrap_content (ver comentario en dialog_chat_ai.xml). Sin tope,
        // una conversación larga estiraría el diálogo hasta salirse de la
        // pantalla. Este listener lo cachea a MAX_ALTURA_CHAT_DP la
        // primera vez que el contenido lo supera, y a partir de ahí queda
        // fijo en ese tope (el ScrollView ya sabe scrollear internamente).
        val maxAlturaPx = (MAX_ALTURA_CHAT_DP * activity.resources.displayMetrics.density).toInt()
        binding.scrollChat.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val actual = binding.scrollChat.layoutParams
                if (actual.height != maxAlturaPx && binding.scrollChat.height > maxAlturaPx) {
                    actual.height = maxAlturaPx
                    binding.scrollChat.layoutParams = actual
                }
            }
        })

        val huertaFoco = huertaIdEnfoque?.let { repository.obtener(it) }
        val contexto = if (huertaFoco != null) {
            buildString {
                append("El usuario está viendo el dashboard en vivo de UNA huerta puntual en SIMONA: ")
                append("${huertaFoco.nombre} (cultivo: ${huertaFoco.categoria}, ")
                append("humedad óptima ${huertaFoco.humedadMin.toInt()}%-${huertaFoco.humedadMax.toInt()}%, ")
                append("pH óptimo ${huertaFoco.phMin}-${huertaFoco.phMax}, ")
                append("temperatura óptima ${huertaFoco.tempMin.toInt()}°C-${huertaFoco.tempMax.toInt()}°C). ")
                val l = huertaFoco.ultimaLectura
                if (l != null) {
                    append("Lectura en vivo: humedad ${l.humedad}%, temperatura ${l.temperatura}°C, ")
                    append("pH ${l.ph}, luz ${l.luz}, tanque de agua ${l.tanqueAgua}%, ")
                    append("riego ${if (l.riegoActivo) "activo" else "inactivo"}. ")
                }
                append("Respondé pensando específicamente en ESTA huerta, no en el resto de la flota.")
            }
        } else {
            val huertas = repository.listar()
            if (huertas.isNotEmpty()) {
                buildString {
                    append("El usuario está en el menú principal de SIMONA. Tiene ${huertas.size} huertas registradas: ")
                    huertas.forEachIndexed { i, h ->
                        append("${i + 1}) ${h.nombre} (Cultivo: ${h.categoria}, humedad óptima: ${h.humedadMin.toInt()}%-${h.humedadMax.toInt()}%). ")
                        val l = h.ultimaLectura
                        if (l != null) {
                            append("Última lectura: humedad ${l.humedad}%, temp ${l.temperatura}°C, riego ${if (l.riegoActivo) "activo" else "inactivo"}. ")
                        }
                    }
                }
            } else {
                "El usuario está en el menú principal de SIMONA. Aún no tiene huertas registradas."
            }
        }

        binding.btnEnviarChat.setOnClickListener {
            val pregunta = binding.etPreguntaChat.text.toString().trim()
            if (pregunta.isEmpty()) return@setOnClickListener

            val textoAnterior = binding.tvHistorialChat.text.toString()
            val etiquetaTu = activity.getString(R.string.asistente_tu)
            val etiquetaSimona = activity.getString(R.string.asistente_simona)
            binding.tvHistorialChat.text =
                "$textoAnterior\n\n$etiquetaTu: $pregunta\n\n${activity.getString(R.string.asistente_pensando)}"
            binding.etPreguntaChat.setText("")
            binding.progressBarChat.visibility = View.VISIBLE
            binding.btnEnviarChat.isEnabled = false
            binding.scrollChat.post { binding.scrollChat.fullScroll(View.FOCUS_DOWN) }

            AsistenteGemini(activity).preguntar(
                pregunta = pregunta,
                contextoHuerta = contexto,
                onExito = { respuesta ->
                    binding.progressBarChat.visibility = View.GONE
                    binding.btnEnviarChat.isEnabled = true
                    binding.tvHistorialChat.text = "$textoAnterior\n\n$etiquetaTu: $pregunta\n\n$etiquetaSimona: $respuesta"
                    binding.scrollChat.post { binding.scrollChat.fullScroll(View.FOCUS_DOWN) }
                },
                onError = { error ->
                    binding.progressBarChat.visibility = View.GONE
                    binding.btnEnviarChat.isEnabled = true
                    binding.tvHistorialChat.text =
                        "$textoAnterior\n\n$etiquetaTu: $pregunta\n\n${activity.getString(R.string.asistente_error_prefijo)}: $error"
                    binding.scrollChat.post { binding.scrollChat.fullScroll(View.FOCUS_DOWN) }
                }
            )
        }

        dialog.show()
    }

    // PLAN_MEJORAS_VISUAL_2.md, punto 4: tope de altura del cuadro de
    // mensajes, aproximadamente lo que ocupaba el 200dp fijo anterior —
    // conserva el comportamiento de conversación larga, sin el espacio
    // vacío del caso corto.
    private const val MAX_ALTURA_CHAT_DP = 200
}
