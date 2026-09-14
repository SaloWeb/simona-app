package com.simona.app

import android.content.Context

/**
 * PLAN_MEJORAS_UX_20.md, punto 19: flag mínimo (mismo patrón que
 * ThemePrefs) para mostrar el tooltip del FAB del asistente de IA
 * "Simona" una única vez, la primera vez que se abre MapaHuertasActivity
 * en el dispositivo.
 */
object OnboardingPrefs {
    private const val PREFS_NAME = "simona_onboarding_prefs"
    private const val KEY_FAB_IA_VISTO = "pref_fab_ia_visto"

    fun fabIaYaVisto(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_FAB_IA_VISTO, false)
    }

    fun marcarFabIaVisto(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FAB_IA_VISTO, true).apply()
    }
}
