package com.simona.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.simona.app.databinding.ItemPerfilCultivoBinding

/**
 * Adapter simple para una lista estática (los 9 perfiles + Personalizado,
 * sección 14.2) — no requiere DiffUtil ni actualizaciones dinámicas, la
 * lista no cambia en tiempo de ejecución.
 */
class PerfilCultivoAdapter(
    private val perfiles: List<PerfilCultivo>,
    private val onPerfilSeleccionado: (PerfilCultivo) -> Unit
) : RecyclerView.Adapter<PerfilCultivoAdapter.PerfilViewHolder>() {

    inner class PerfilViewHolder(val binding: ItemPerfilCultivoBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PerfilViewHolder {
        val binding = ItemPerfilCultivoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PerfilViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PerfilViewHolder, position: Int) {
        val perfil = perfiles[position]
        holder.binding.ivIconoPerfil.setImageResource(perfil.iconoRes)
        holder.binding.tvNombre.text = perfil.nombre
        holder.binding.tvEjemplos.text = perfil.ejemplos
        holder.binding.root.setOnClickListener { onPerfilSeleccionado(perfil) }
    }

    override fun getItemCount(): Int = perfiles.size
}
