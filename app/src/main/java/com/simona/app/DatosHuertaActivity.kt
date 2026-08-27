
package com.simona.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.simona.app.databinding.ActivityDatosHuertaBinding
import java.util.UUID

/**
 * Paso 3 del flujo de creación de huerta (Plan de Desarrollo, sección 14.1
 * y 14.4). Recibe el perfil y los 8 rangos ya confirmados en D.2
 * (AjustarRangosActivity) vía extras del Intent, pide nombre + contraseña
 * de red (el SSID se mantiene fijo en "SIMONA", sección 14.4) y deja
 * marcar opcionalmente la posición en un croquis genérico. Al guardar,
 * arma el objeto Huerta, lo persiste en HuertaRepository y pasa a D.4
 * (TutorialConexionActivity) para conectar con esa huerta.
 */
class DatosHuertaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDatosHuertaBinding

    // Posición relativa (0f–1f) dentro del croquis; null si el usuario no
    // tocó la pantalla — el paso es opcional (sección 14.4).
    private var posicionMapaX: Float? = null
    private var posicionMapaY: Float? = null

    // Fase 6.4: foto opcional de la huerta. Se usa en el mapa compartido
    // (MapaHuertasActivity) en vez del rectángulo de color del bancal.
    private var fotoUri: Uri? = null

    private val seleccionarFoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            FotoHuertaUtil.persistirPermisoLectura(contentResolver, uri)
            fotoUri = uri
            mostrarPreviewFoto(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDatosHuertaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupCroquis()
        setupFoto()
        binding.btnVolver.setOnClickListener { finish() }
        binding.btnGuardar.setOnClickListener { guardarHuerta() }
    }

    private fun setupFoto() {
        binding.btnElegirFoto.setOnClickListener {
            seleccionarFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.btnQuitarFoto.setOnClickListener {
            fotoUri = null
            binding.ivFotoPreview.setImageDrawable(null)
            binding.ivFotoPreview.visibility = View.GONE
            binding.tvFotoHint.visibility = View.VISIBLE
            binding.btnQuitarFoto.visibility = View.GONE
        }
    }

    private fun mostrarPreviewFoto(uri: Uri) {
        val destinoPx = 64.dpToPx()
        val bitmap = FotoHuertaUtil.decodificarSampleado(this, uri, destinoPx, destinoPx)
        if (bitmap == null) {
            // URI ya no accesible: no se agrega la foto, se sigue como si
            // no se hubiera elegido ninguna.
            fotoUri = null
            return
        }
        FotoHuertaUtil.aplicarConEsquinasRedondeadas(this, binding.ivFotoPreview, bitmap)
        binding.ivFotoPreview.visibility = View.VISIBLE
        binding.tvFotoHint.visibility = View.GONE
        binding.btnQuitarFoto.visibility = View.VISIBLE
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    /**
     * Toca/arrastra sobre el croquis genérico para fijar el marcador. Se
     * guarda como posición RELATIVA (0f–1f), no en píxeles, para que tenga
     * sentido sin importar el tamaño de pantalla en que se dibuje después
     * (sección 14.6, vista Mapa — todavía no implementada).
     */
    private fun setupCroquis() {
        binding.croquis.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val x = event.x.coerceIn(0f, view.width.toFloat())
                    val y = event.y.coerceIn(0f, view.height.toFloat())
                    posicionMapaX = x / view.width
                    posicionMapaY = y / view.height
                    mostrarMarcador(x, y)
                    true
                }
                else -> false
            }
        }
    }

    private fun mostrarMarcador(x: Float, y: Float) {
        binding.tvCroquisHint.visibility = View.GONE
        binding.marcador.visibility = View.VISIBLE
        binding.marcador.translationX = x - binding.marcador.width / 2f
        binding.marcador.translationY = y - binding.marcador.height / 2f
    }

    private fun guardarHuerta() {
        val nombre = binding.etNombre.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()

        var valido = true

        if (nombre.isEmpty()) {
            binding.tvErrorNombre.visibility = View.VISIBLE
            valido = false
        } else {
            binding.tvErrorNombre.visibility = View.GONE
        }

        // WPA2 exige mínimo 8 caracteres: WifiNetworkSpecifier.setWpa2Passphrase()
        // lanza IllegalArgumentException con contraseñas más cortas (sección
        // 8.3 del Plan de Desarrollo), así que se valida acá antes de guardar.
        if (password.length < 8) {
            binding.tvErrorPassword.visibility = View.VISIBLE
            valido = false
        } else {
            binding.tvErrorPassword.visibility = View.GONE
        }

        if (!valido) return

        val perfilId = intent.getStringExtra(AjustarRangosActivity.EXTRA_PERFIL_ID)
        val perfil = PerfilesCultivo.porId(perfilId ?: "")
            ?: PerfilesCultivo.porId("personalizado")!!

        // Si por algún motivo faltara un extra (por ejemplo, alguien abrió
        // esta pantalla directo por adb sin pasar por D.2), se cae a los
        // valores del perfil en vez de romper.
        val humedadMin = intent.getFloatExtra(AjustarRangosActivity.EXTRA_HUMEDAD_MIN, perfil.humedadMin)
        val humedadMax = intent.getFloatExtra(AjustarRangosActivity.EXTRA_HUMEDAD_MAX, perfil.humedadMax)
        val phMin = intent.getFloatExtra(AjustarRangosActivity.EXTRA_PH_MIN, perfil.phMin)
        val phMax = intent.getFloatExtra(AjustarRangosActivity.EXTRA_PH_MAX, perfil.phMax)
        val luzMin = intent.getFloatExtra(AjustarRangosActivity.EXTRA_LUZ_MIN, perfil.luzMin.toFloat())
        val luzMax = intent.getFloatExtra(AjustarRangosActivity.EXTRA_LUZ_MAX, perfil.luzMax.toFloat())
        val tempMin = intent.getFloatExtra(AjustarRangosActivity.EXTRA_TEMP_MIN, perfil.tempMin)
        val tempMax = intent.getFloatExtra(AjustarRangosActivity.EXTRA_TEMP_MAX, perfil.tempMax)

        val huerta = Huerta(
            id = UUID.randomUUID().toString(),
            nombre = nombre,
            passwordRed = password,
            categoria = perfil.nombre,
            humedadMin = humedadMin,
            humedadMax = humedadMax,
            phMin = phMin,
            phMax = phMax,
            luzMin = luzMin.toInt(),
            luzMax = luzMax.toInt(),
            tempMin = tempMin,
            tempMax = tempMax,
            posicionMapaX = posicionMapaX,
            posicionMapaY = posicionMapaY,
            fotoUri = fotoUri?.toString()
        )

        (application as SimonaApp).huertaRepository.guardar(huerta)

        // D.4: en vez de solo confirmar con un Toast y volver a MainActivity,
        // se pasa directo a la pantalla de conexión de ESTA huerta (14.5),
        // que ya sabe usar huerta.passwordRed en vez de la fija de antes.
        startActivity(TutorialConexionActivity.crearIntent(this, huerta.id))
        finish()
    }

    companion object {
        /** Arma el Intent reutilizando los extras que ya trae AjustarRangosActivity. */
        fun crearIntent(context: Context, datosOrigen: Intent): Intent =
            Intent(context, DatosHuertaActivity::class.java).apply {
                putExtras(datosOrigen)
            }
    }
}
