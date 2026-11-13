package com.app.transporte_chota_bus.domain.validation

enum class CampoPerfil { NOMBRE, DNI, CELULAR }

// Comprueba los datos personales que el usuario puede modificar.
object ValidadorPerfil {
    const val MAXIMO_NOMBRE = 200

    fun validar(nombre: String, dni: String, celular: String): Map<CampoPerfil, String> = buildMap {
        when {
            nombre.isBlank() -> put(CampoPerfil.NOMBRE, "Ingresa tus nombres y apellidos")
            nombre.trim().length > MAXIMO_NOMBRE -> put(CampoPerfil.NOMBRE, "El nombre admite hasta 200 caracteres")
        }
        if (!dni.trim().matches(Regex("[0-9]{8}"))) {
            put(CampoPerfil.DNI, "Ingresa un DNI de 8 dígitos")
        }
        if (!celular.trim().matches(Regex("[0-9]{9}"))) {
            put(CampoPerfil.CELULAR, "Ingresa un celular de 9 dígitos")
        }
    }
}
