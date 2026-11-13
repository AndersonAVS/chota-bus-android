package com.app.transporte_chota_bus.ui.auth

enum class CampoAcceso { CORREO, CONTRASENA }

// Comprueba los campos de acceso y recuperación antes de consultar el servicio.
object ValidadorAcceso {
    private val formatoCorreo = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun validarCorreo(correo: String): String? = when {
        correo.isBlank() -> "Ingresa tu correo electrónico"
        !formatoCorreo.matches(correo.trim()) -> "Ingresa un correo electrónico válido"
        else -> null
    }

    fun validarLogin(correo: String, contrasena: String): Map<CampoAcceso, String> = buildMap {
        validarCorreo(correo)?.let { put(CampoAcceso.CORREO, it) }
        if (contrasena.isEmpty()) {
            put(CampoAcceso.CONTRASENA, "Ingresa tu contraseña")
        }
    }

    fun validarRecuperacion(correo: String): Map<CampoAcceso, String> = buildMap {
        validarCorreo(correo)?.let { put(CampoAcceso.CORREO, it) }
    }
}
