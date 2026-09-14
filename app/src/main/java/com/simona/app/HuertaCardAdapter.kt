package com.simona.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.simona.app.databinding.ItemHuertaCardBinding

/**
 * Adapter de la lista de huertas del Home (PLAN_MEJORAS_20.md, punto 7).
 * Antes MapaHuertasActivity.renderizarTarjetas() armaba cada tarjeta a
 * mano agregando Views por código a un LinearLayout; ahora es un
 * RecyclerView con ListAdapter + DiffUtil, y cada tarjeta es
 * item_huerta_card.xml inflado por ViewBinding. La lógica de colores y
 * estado por huerta es la misma que tenía renderizarTarjetas(), solo
 * movida al bind() del ViewHolder.
 */
class HuertaCardAdapter(
    private val onClick: (Huerta) -> Unit,
    private val onLongClick: (Huerta) -> Unit,
    private val onEstadoClick: (Huerta) -> Unit,
    private val onOpcionesClick: (Huerta) -> Unit
) : ListAdapter<Huerta, HuertaCardAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHuertaCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemHuertaCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(huerta: Huerta) {
            val ctx = binding.root.context
            val colorTinta = ContextCompat.getColor(ctx, R.color.simona_tinta)
            val colorSuave = ContextCompat.getColor(ctx, R.color.simona_tinta_suave)
            val colorAzul = ContextCompat.getColor(ctx, R.color.simona_azul)
            val colorRojo = ContextCompat.getColor(ctx, R.color.simona_rojo)
            val colorVerde = ContextCompat.getColor(ctx, R.color.simona_verde)
            val colorMarron = ContextCompat.getColor(ctx, R.color.simona_marron)

            val l = huerta.ultimaLectura
            val colorFranja = when {
                l == null -> colorMarron
                l.humedad <= huerta.humedadMin -> colorRojo
                l.humedad >= huerta.humedadMax -> colorAzul
                else -> colorVerde
            }
            binding.franjaEstado.setBackgroundColor(colorFranja)

            binding.wrapper.setOnClickListener { onClick(huerta) }
            binding.wrapper.setOnLongClickListener { onLongClick(huerta); true }

            binding.tvNombre.text = huerta.nombre
            binding.tvRango.text = ctx.getString(
                R.string.home_rango_ideal,
                huerta.categoria,
                huerta.humedadMin.toInt(),
                huerta.humedadMax.toInt()
            )

            binding.ivMas.contentDescription = ctx.getString(R.string.opciones_huerta_cd, huerta.nombre)
            binding.ivMas.setOnClickListener { onOpcionesClick(huerta) }

            if (l != null) {
                val esSeco = l.humedad <= huerta.humedadMin
                val esHumedo = l.humedad >= huerta.humedadMax
                val colorEstado = when {
                    esSeco -> colorRojo
                    esHumedo -> colorAzul
                    else -> colorVerde
                }
                val textoEstado = ctx.getString(
                    when {
                        esSeco -> R.string.home_estado_seco
                        esHumedo -> R.string.home_estado_humedo
                        else -> R.string.home_estado_optimo
                    }
                )

                binding.ivEstadoIcono.setImageResource(R.drawable.ic_gota_full)
                binding.ivEstadoIcono.imageTintList = ColorStateList.valueOf(colorEstado)

                binding.tvEstado.text = ctx.getString(R.string.home_humedad_estado, l.humedad.toInt(), textoEstado)
                binding.tvEstado.setTextColor(colorEstado)
                binding.tvEstado.foreground = obtenerRippleTematico(ctx)
                binding.tvEstado.isClickable = true
                binding.tvEstado.isFocusable = true
                binding.tvEstado.setOnClickListener { onEstadoClick(huerta) }
            } else {
                binding.ivEstadoIcono.setImageResource(R.drawable.ic_brote)
                binding.ivEstadoIcono.imageTintList = null

                binding.tvEstado.text = ctx.getString(R.string.home_sin_lecturas)
                binding.tvEstado.setTextColor(colorMarron)
                binding.tvEstado.foreground = null
                binding.tvEstado.isClickable = false
                binding.tvEstado.isFocusable = false
                binding.tvEstado.setOnClickListener(null)
            }
        }
    }

    companion object {
        private fun obtenerRippleTematico(ctx: Context): Drawable? {
            val valor = TypedValue()
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, valor, true)
            return ContextCompat.getDrawable(ctx, valor.resourceId)
        }

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Huerta>() {
            override fun areItemsTheSame(oldItem: Huerta, newItem: Huerta) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Huerta, newItem: Huerta) = oldItem == newItem
        }
    }
}
