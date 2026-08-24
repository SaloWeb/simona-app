package com.simona.app

/**
 * Perfil de cultivo de partida (sección 14.2). Los valores son estimaciones
 * de referencia, editables por el usuario en el paso siguiente (D.2,
 * sliders de rangos) — funcionan como punto de partida, no como valores
 * fijos. Ver nota metodológica de la sección 14.2 sobre validar estos
 * rangos contra una fuente agronómica (ej. INTA) antes de un uso real.
 */
data class PerfilCultivo(
    val id: String,
    val nombre: String,
    val ejemplos: String,
    val iconoRes: Int,
    val humedadMin: Float,
    val humedadMax: Float,
    val phMin: Float,
    val phMax: Float,
    val luzMin: Int,
    val luzMax: Int,
    val tempMin: Float,
    val tempMax: Float
)

/**
 * Los 9 perfiles de partida de la sección 14.2 (8 categorías hortícolas +
 * Cactus/Suculenta) más la opción "Personalizado" con valores neutros.
 * Lista estática: no requiere red ni base de datos, se usa tal cual en D.1.
 */
object PerfilesCultivo {

    val lista: List<PerfilCultivo> = listOf(
        PerfilCultivo(
            id = "hoja", nombre = "Hoja", ejemplos = "Lechuga, espinaca, acelga, rúcula",
            iconoRes = R.drawable.ic_perfil_hoja,
            humedadMin = 40f, humedadMax = 65f,
            phMin = 6.0f, phMax = 7.0f,
            luzMin = 300, luzMax = 650,
            tempMin = 15f, tempMax = 24f
        ),
        PerfilCultivo(
            id = "fruto", nombre = "Fruto", ejemplos = "Tomate, pimiento, berenjena, calabacín",
            iconoRes = R.drawable.ic_perfil_fruto,
            humedadMin = 35f, humedadMax = 60f,
            phMin = 6.0f, phMax = 6.8f,
            luzMin = 500, luzMax = 850,
            tempMin = 18f, tempMax = 28f
        ),
        PerfilCultivo(
            id = "raiz", nombre = "Raíz", ejemplos = "Zanahoria, rábano, remolacha, nabo",
            iconoRes = R.drawable.ic_perfil_raiz,
            humedadMin = 30f, humedadMax = 55f,
            phMin = 5.8f, phMax = 6.8f,
            luzMin = 300, luzMax = 600,
            tempMin = 15f, tempMax = 22f
        ),
        PerfilCultivo(
            id = "tallo", nombre = "Tallo", ejemplos = "Apio, espárrago",
            iconoRes = R.drawable.ic_perfil_tallo,
            humedadMin = 40f, humedadMax = 60f,
            phMin = 6.0f, phMax = 6.8f,
            luzMin = 400, luzMax = 700,
            tempMin = 15f, tempMax = 24f
        ),
        PerfilCultivo(
            id = "flor", nombre = "Flor", ejemplos = "Brócoli, coliflor, alcachofa",
            iconoRes = R.drawable.ic_perfil_flor,
            humedadMin = 40f, humedadMax = 65f,
            phMin = 6.0f, phMax = 7.0f,
            luzMin = 400, luzMax = 700,
            tempMin = 15f, tempMax = 24f
        ),
        PerfilCultivo(
            id = "bulbo", nombre = "Bulbo", ejemplos = "Cebolla, ajo, puerro",
            iconoRes = R.drawable.ic_perfil_bulbo,
            humedadMin = 30f, humedadMax = 50f,
            phMin = 6.0f, phMax = 7.0f,
            luzMin = 300, luzMax = 600,
            tempMin = 15f, tempMax = 22f
        ),
        PerfilCultivo(
            id = "aromaticas", nombre = "Aromáticas", ejemplos = "Albahaca, menta, romero, tomillo",
            iconoRes = R.drawable.ic_perfil_aromaticas,
            humedadMin = 25f, humedadMax = 45f,
            phMin = 5.5f, phMax = 6.5f,
            luzMin = 400, luzMax = 800,
            tempMin = 15f, tempMax = 26f
        ),
        PerfilCultivo(
            id = "legumbres", nombre = "Legumbres", ejemplos = "Arvejas, habas, maíz",
            iconoRes = R.drawable.ic_perfil_legumbres,
            humedadMin = 35f, humedadMax = 60f,
            phMin = 6.0f, phMax = 7.0f,
            luzMin = 400, luzMax = 700,
            tempMin = 15f, tempMax = 25f
        ),
        PerfilCultivo(
            id = "cactus", nombre = "Cactus / Suculenta", ejemplos = "No hortícola",
            iconoRes = R.drawable.ic_perfil_cactus,
            humedadMin = 10f, humedadMax = 25f,
            phMin = 6.0f, phMax = 7.5f,
            luzMin = 600, luzMax = 950,
            tempMin = 20f, tempMax = 32f
        ),
        PerfilCultivo(
            id = "personalizado", nombre = "Personalizado", ejemplos = "Partir de valores neutros",
            iconoRes = R.drawable.ic_perfil_personalizado,
            humedadMin = 30f, humedadMax = 60f,
            phMin = 6.0f, phMax = 7.0f,
            luzMin = 400, luzMax = 700,
            tempMin = 18f, tempMax = 26f
        )
    )

    fun porId(id: String): PerfilCultivo? = lista.find { it.id == id }
}
