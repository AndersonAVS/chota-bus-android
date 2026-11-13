package com.app.transporte_chota_bus.ui.auth

enum class CampoRegistro {
    NOMBRE, DNI, CELULAR, CORREO, CONTRASENA, CONFIRMACION, TERMINOS
}

// Comprueba los datos del registro sin crear ni almacenar una cuenta.
object ValidadorRegistro {
    const val LONGITUD_MINIMA_CONTRASENA = 8

    fun validar(
        nombre: String,
        dni: String,
        celular: String,
        correo: String,
        contrasena: String,
        confirmacion: String,
        aceptaTerminos: Boolean
    ): Map<CampoRegistro, String> = buildMap {
        if (nombre.isBlank()) {
            put(CampoRegistro.NOMBRE, "Ingresa tus nombres y apellidos")
        }
        if (!dni.trim().matches(Regex("[0-9]{8}"))) {
            put(CampoRegistro.DNI, "Ingresa un DNI de 8 dígitos")
        }
        if (!celular.trim().matches(Regex("[0-9]{9}"))) {
            put(CampoRegistro.CELULAR, "Ingresa un celular de 9 dígitos")
        }
        if (ValidadorAcceso.validarCorreo(correo) != null) {
            put(CampoRegistro.CORREO, "Ingresa un correo electrónico válido")
        }
        if (contrasena.isBlank() || contrasena.length < LONGITUD_MINIMA_CONTRASENA) {
            put(CampoRegistro.CONTRASENA, "Utiliza al menos 8 caracteres")
        }
        if (confirmacion.isEmpty()) {
            put(CampoRegistro.CONFIRMACION, "Confirma tu contraseña")
        } else if (contrasena != confirmacion) {
            put(CampoRegistro.CONFIRMACION, "Las contraseñas no coinciden")
        }
        if (!aceptaTerminos) {
            put(CampoRegistro.TERMINOS, "Acepta los términos y la política de privacidad")
        }
    }
}
