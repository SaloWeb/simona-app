package com.simona.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.simona.app.databinding.ActivitySeleccionarPerfilBinding

/**
 * Paso 1 del flujo de creación de huerta (sección 14.1 y 14.2): elegir un
 * perfil de cultivo de partida entre las 9 tarjetas (8 categorías
 * hortícolas + Cactus/Suculenta) más la opción "Personalizado".
 *
 * Cambio respecto a la versión de D.1: antes, tocar una tarjeta solo hacía
 * setResult(RESULT_OK) y cerraba la pantalla (para probarla de forma
 * standalone, sin nada del otro lado). Ahora avanza al Paso 2
 * (AjustarRangosActivity, D.2), pasándole el id del perfil elegido — así
 * el flujo completo (14.1) queda: Paso 1 → Paso 2 → ... en vez de terminar
 * en la nada.
 */
class SeleccionarPerfilActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySeleccionarPerfilBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySeleccionarPerfilBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvPerfiles.layoutManager = GridLayoutManager(this, 2)
        binding.rvPerfiles.adapter = PerfilCultivoAdapter(PerfilesCultivo.lista) { perfil ->
            seleccionarPerfil(perfil)
        }
    }

    private fun seleccionarPerfil(perfil: PerfilCultivo) {
        startActivity(AjustarRangosActivity.crearIntent(this, perfil.id))
        // No se llama a finish(): si el usuario vuelve con el botón atrás
        // desde AjustarRangosActivity, queda en esta misma pantalla para
        // elegir otro perfil (sección 14.3, "Navegación hacia atrás").
    }

    companion object {
        const val EXTRA_PERFIL_ID = "extra_perfil_id"

        fun crearIntent(context: Context): Intent =
            Intent(context, SeleccionarPerfilActivity::class.java)
    }
}