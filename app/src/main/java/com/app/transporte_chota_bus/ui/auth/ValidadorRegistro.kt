package com.app.transporte_chota_bus.ui.auth

import com.app.transporte_chota_bus.domain.validation.CampoPerfil
import com.app.transporte_chota_bus.domain.validation.ValidadorPerfil

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
        ValidadorPerfil.validar(nombre, dni, celular).forEach { (campo, mensaje) ->
            val campoRegistro = when (campo) {
                CampoPerfil.NOMBRE -> CampoRegistro.NOMBRE
                CampoPerfil.DNI -> CampoRegistro.DNI
                CampoPerfil.CELULAR -> CampoRegistro.CELULAR
            }
            put(campoRegistro, mensaje)
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
