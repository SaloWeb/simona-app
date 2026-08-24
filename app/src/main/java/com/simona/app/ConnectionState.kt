package com.simona.app

/**
 * Representa los estados posibles de la conexión con SIMONA, para que la UI
 * reaccione de forma clara (Documento de Plan, sección 4.1).
 */
sealed class ConnectionState {
    object Desconectado : ConnectionState()
    object Conectando : ConnectionState()
    object Conectado : ConnectionState()
    data class Error(val mensaje: String) : ConnectionState()
}